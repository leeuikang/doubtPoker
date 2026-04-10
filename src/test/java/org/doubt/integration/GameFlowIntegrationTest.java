package org.doubt.integration;

import org.doubt.auth.GuestTokenService;
import org.doubt.doubtpoker.DoubtPokerApplication;
import org.doubt.constant.ErrorCode;
import org.doubt.dto.GameMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게임 플로우 통합 테스트
 * 토큰 발급 → STOMP CONNECT → 방 입장 → 메시지 수신 전체 흐름 검증
 */
@SpringBootTest(classes = DoubtPokerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("게임 플로우 통합 테스트")
class GameFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private GuestTokenService guestTokenService;

    private WebSocketStompClient stompClient;
    private String wsUrl;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())))
        );
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        wsUrl = "http://localhost:" + port + "/websocket";
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    private StompSession connectWithNickname(String nickname) throws Exception {
        String token = guestTokenService.issue(nickname);
        String guestId = guestTokenService.verify(token).guestId();
        String csrfToken = guestTokenService.issueCsrfToken(guestId);
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync(
                wsUrl + "?csrf=" + csrfToken,
                new WebSocketHttpHeaders(), headers, new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    @Nested
    @DisplayName("방 입장 플로우")
    class JoinRoomFlow {

        @Test
        @DisplayName("인증 후 방 토픽을 구독하고 JOIN 메시지를 전송하면 브로드캐스트를 수신한다")
        void join_room_broadcasts_to_subscribers() throws Exception {
            String roomId = "flow-room-1";
            String nickname = "플로우테스트유저";

            StompSession session = connectWithNickname(nickname);

            BlockingQueue<GameMessage> received = new LinkedBlockingQueue<>();
            session.subscribe("/topic/room/" + roomId, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return GameMessage.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    if (payload instanceof GameMessage msg) {
                        received.offer(msg);
                    }
                }
            });

            // JOIN 메시지 전송 (sender는 사칭값 — 서버가 세션 nickname으로 교체해야 함)
            GameMessage joinMsg = new GameMessage("JOIN", roomId, "사칭자", null);
            session.send("/app/game/join", joinMsg);

            GameMessage broadcast = received.poll(5, TimeUnit.SECONDS);

            assertThat(broadcast).isNotNull();
            // sender가 사칭자 아닌 세션 nickname으로 교체되었음을 검증
            assertThat(broadcast.sender()).isEqualTo(nickname);
            assertThat(broadcast.roomId()).isEqualTo(roomId);

            session.disconnect();
        }

        @Test
        @DisplayName("두 클라이언트가 같은 방에 입장하면 서로의 입장 메시지를 수신한다")
        void two_clients_receive_each_others_join_messages() throws Exception {
            String roomId = "flow-room-2";

            StompSession session1 = connectWithNickname("멀티유저A");
            StompSession session2 = connectWithNickname("멀티유저B");

            BlockingQueue<GameMessage> received1 = new LinkedBlockingQueue<>();
            BlockingQueue<GameMessage> received2 = new LinkedBlockingQueue<>();

            session1.subscribe("/topic/room/" + roomId, gameMessageHandler(received1));
            session2.subscribe("/topic/room/" + roomId, gameMessageHandler(received2));

            // 두 클라이언트 모두 방 입장
            session1.send("/app/game/join", new GameMessage("JOIN", roomId, "ignored", null));
            session2.send("/app/game/join", new GameMessage("JOIN", roomId, "ignored", null));

            // 두 큐에서 각각 메시지 수신 확인
            GameMessage msg1 = received1.poll(5, TimeUnit.SECONDS);
            GameMessage msg2 = received2.poll(5, TimeUnit.SECONDS);

            assertThat(msg1).isNotNull();
            assertThat(msg2).isNotNull();

            session1.disconnect();
            session2.disconnect();
        }
    }

    @Nested
    @DisplayName("게임 액션 에러 검증")
    class GameActionErrorFlow {

        @Test
        @DisplayName("다른 방의 roomId로 액션을 전송하면 NOT_IN_ROOM 에러를 수신한다")
        void action_to_wrong_room_receives_not_in_room_error() throws Exception {
            String joinedRoom = "flow-room-3";
            String otherRoom  = "flow-room-99";
            String nickname   = "오류테스트유저";

            StompSession session = connectWithNickname(nickname);

            BlockingQueue<GameMessage> errorQueue = new LinkedBlockingQueue<>();
            session.subscribe("/user/queue/errors", gameMessageHandler(errorQueue));

            // joinedRoom에 입장
            session.send("/app/game/join", new GameMessage("JOIN", joinedRoom, "ignored", null));
            Thread.sleep(500);

            // 다른 방 roomId로 액션 시도 → NOT_IN_ROOM 에러
            session.send("/app/game/action", new GameMessage("DRAW", otherRoom, nickname, null));

            GameMessage error = errorQueue.poll(5, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.payload().toString()).contains(ErrorCode.NOT_IN_ROOM.getMessage());

            session.disconnect();
        }

        @Test
        @DisplayName("알 수 없는 액션 타입을 전송하면 INVALID_TURN_PHASE 에러를 수신한다")
        void unknown_action_type_receives_invalid_turn_phase_error() throws Exception {
            String roomId   = "flow-room-4";
            String nickname = "액션오류유저";

            StompSession session = connectWithNickname(nickname);

            BlockingQueue<GameMessage> errorQueue = new LinkedBlockingQueue<>();
            session.subscribe("/user/queue/errors", gameMessageHandler(errorQueue));

            session.send("/app/game/join", new GameMessage("JOIN", roomId, "ignored", null));
            Thread.sleep(500);

            // 존재하지 않는 액션 타입
            session.send("/app/game/action", new GameMessage("UNKNOWN_ACTION", roomId, nickname, null));

            GameMessage error = errorQueue.poll(5, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.payload().toString())
                    .contains(ErrorCode.INVALID_TURN_PHASE.getMessage());

            session.disconnect();
        }
    }

    private StompFrameHandler gameMessageHandler(BlockingQueue<GameMessage> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (payload instanceof GameMessage msg) {
                    queue.offer(msg);
                }
            }
        };
    }
}
