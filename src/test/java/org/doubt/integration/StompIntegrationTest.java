package org.doubt.integration;

import org.doubt.auth.GuestTokenService;
import org.doubt.doubtpoker.DoubtPokerApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STOMP CONNECT 인증 통합 테스트 (H-2)
 * 실제 WebSocket 연결로 StompAuthInterceptor 동작 검증
 */
@SpringBootTest(classes = DoubtPokerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("STOMP 인증 통합 테스트")
class StompIntegrationTest {

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
        wsUrl = "http://localhost:" + port + "/websocket";
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    /** 유효한 Bearer 토큰으로 CONNECT → 세션 수립 성공 */
    private StompSession connectWithToken(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        return stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    @Nested
    @DisplayName("CONNECT 성공")
    class ConnectSuccess {

        @Test
        @DisplayName("유효한 토큰으로 CONNECT 하면 세션이 수립된다")
        void valid_token_establishes_session() throws Exception {
            String token = guestTokenService.issue("통합테스트유저");
            StompSession session = connectWithToken(token);

            assertThat(session.isConnected()).isTrue();
            session.disconnect();
        }

        @Test
        @DisplayName("서로 다른 닉네임의 두 클라이언트가 동시에 연결 가능하다")
        void two_different_nicknames_can_connect_simultaneously() throws Exception {
            String token1 = guestTokenService.issue("유저A");
            String token2 = guestTokenService.issue("유저B");

            StompSession session1 = connectWithToken(token1);
            StompSession session2 = connectWithToken(token2);

            assertThat(session1.isConnected()).isTrue();
            assertThat(session2.isConnected()).isTrue();

            session1.disconnect();
            session2.disconnect();
        }
    }

    @Nested
    @DisplayName("CONNECT 실패")
    class ConnectFailure {

        @Test
        @DisplayName("Authorization 헤더 없이 CONNECT 하면 연결에 실패한다")
        void connect_without_token_fails() {
            CompletableFuture<StompSession> future = stompClient.connectAsync(
                    wsUrl,
                    new WebSocketHttpHeaders(),
                    new StompHeaders(),
                    new StompSessionHandlerAdapter() {}
            );

            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("잘못된 서명의 토큰으로 CONNECT 하면 연결에 실패한다")
        void connect_with_invalid_token_fails() {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer invalid.token.value");

            CompletableFuture<StompSession> future = stompClient.connectAsync(
                    wsUrl,
                    new WebSocketHttpHeaders(),
                    connectHeaders,
                    new StompSessionHandlerAdapter() {}
            );

            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("중복 닉네임으로 CONNECT 하면 두 번째 연결에 실패한다")
        void connect_with_duplicate_nickname_fails() throws Exception {
            String token1 = guestTokenService.issue("중복닉네임");
            StompSession session1 = connectWithToken(token1);
            assertThat(session1.isConnected()).isTrue();

            String token2 = guestTokenService.issue("중복닉네임");
            CompletableFuture<StompSession> future = stompClient.connectAsync(
                    wsUrl,
                    new WebSocketHttpHeaders(),
                    buildAuthHeaders(token2),
                    new StompSessionHandlerAdapter() {}
            );

            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(Exception.class);

            session1.disconnect();
        }

        @Test
        @DisplayName("다른 서버 인스턴스가 발급한 토큰(재시작 시뮬레이션)으로 CONNECT 하면 실패한다")
        void connect_with_other_instance_token_fails() {
            // 별도 GuestTokenService 인스턴스(다른 instanceId)로 발급한 토큰
            GuestTokenService otherInstance = new GuestTokenService(
                    "doubt-poker-secret-key-change-in-production", 1
            );
            String staleToken = otherInstance.issue("재시작전유저");

            StompHeaders connectHeaders = buildAuthHeaders(staleToken);

            CompletableFuture<StompSession> future = stompClient.connectAsync(
                    wsUrl,
                    new WebSocketHttpHeaders(),
                    connectHeaders,
                    new StompSessionHandlerAdapter() {}
            );

            assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("구독")
    class Subscribe {

        @Test
        @DisplayName("인증 후 방 토픽을 구독할 수 있다")
        void authenticated_session_can_subscribe_to_room_topic() throws Exception {
            String token = guestTokenService.issue("구독테스트유저");
            StompSession session = connectWithToken(token);

            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            session.subscribe("/topic/room/room-1", new StompSessionHandlerAdapter() {
                @Override
                public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    received.offer(payload != null ? payload.toString() : "");
                }
            });

            // 구독 자체가 예외 없이 완료되면 성공
            assertThat(session.isConnected()).isTrue();
            session.disconnect();
        }
    }

    private StompHeaders buildAuthHeaders(String token) {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return headers;
    }
}
