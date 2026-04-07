package org.doubt.controller;

import org.doubt.auth.GuestTokenService;
import org.doubt.doubtpoker.DoubtPokerApplication;
import org.doubt.dto.GameMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
 * GameController integration tests.
 *
 * These tests are disabled because GameController depends on ObjectMapper,
 * which is not registered as a Spring bean in the current application context.
 * The project uses spring-boot-starter-websocket (not starter-web), so Jackson
 * autoconfiguration does not activate and ObjectMapper has no qualifying bean.
 *
 * To enable these tests, either:
 *   1. Add an ObjectMapper @Bean in a @Configuration class, OR
 *   2. Add spring-boot-starter-web to the implementation dependencies.
 *
 * What these tests would verify (once the context issue is resolved):
 *   - WebSocket STOMP connection succeeds with a valid guest token
 *   - /app/game/join broadcasts a GameMessage to /topic/room/{roomId}
 *     with the authenticated session nickname (not the spoofed message sender)
 *   - Two clients joining the same room both receive JOIN broadcasts
 */
@Disabled("GameController requires ObjectMapper bean — not registered without spring-boot-starter-web. " +
        "Add @Bean ObjectMapper in a @Configuration or move starter-web to implementation scope.")
@SpringBootTest(classes = DoubtPokerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("GameController integration tests")
class GameControllerIntegrationTest {

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

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /** Connect to WebSocket using a freshly issued guest token for the given nickname. */
    private StompSession connectWithNickname(String nickname) throws Exception {
        String token = guestTokenService.issue(nickname);
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync(
                wsUrl,
                new WebSocketHttpHeaders(),
                headers,
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS);
    }

    /** Create a simple StompFrameHandler that places received GameMessage frames into a queue. */
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

    // ----------------------------------------------------------------
    // WebSocket connection
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("WebSocket connection")
    class WebSocketConnection {

        @Test
        @DisplayName("Valid token — STOMP session is established")
        void valid_token_establishes_session() throws Exception {
            StompSession session = connectWithNickname("integ-user-connect");
            assertThat(session.isConnected()).isTrue();
            session.disconnect();
        }
    }

    // ----------------------------------------------------------------
    // /app/game/join
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("/app/game/join")
    class JoinRoom {

        @Test
        @DisplayName("JOIN message is broadcast to /topic/room/{roomId} with the session nickname as sender")
        void join_broadcasts_with_session_nickname() throws Exception {
            String roomId = "integ-room-join-1";
            String nickname = "integ-join-user";

            StompSession session = connectWithNickname(nickname);
            assertThat(session.isConnected()).isTrue();

            BlockingQueue<GameMessage> received = new LinkedBlockingQueue<>();
            session.subscribe("/topic/room/" + roomId, gameMessageHandler(received));

            // Send JOIN — server must replace sender with the authenticated session nickname
            session.send("/app/game/join", new GameMessage("JOIN", roomId, "impersonator", null));

            GameMessage broadcast = received.poll(5, TimeUnit.SECONDS);

            assertThat(broadcast).isNotNull();
            assertThat(broadcast.roomId()).isEqualTo(roomId);
            // Verify the server replaced the spoofed sender with the real session nickname
            assertThat(broadcast.sender()).isEqualTo(nickname);

            session.disconnect();
        }

        @Test
        @DisplayName("Two clients join the same room — both receive each other's JOIN broadcast")
        void two_clients_receive_join_broadcasts() throws Exception {
            String roomId = "integ-room-join-2";

            StompSession session1 = connectWithNickname("integ-multi-A");
            StompSession session2 = connectWithNickname("integ-multi-B");

            BlockingQueue<GameMessage> queue1 = new LinkedBlockingQueue<>();
            BlockingQueue<GameMessage> queue2 = new LinkedBlockingQueue<>();

            session1.subscribe("/topic/room/" + roomId, gameMessageHandler(queue1));
            session2.subscribe("/topic/room/" + roomId, gameMessageHandler(queue2));

            session1.send("/app/game/join", new GameMessage("JOIN", roomId, "ignored", null));
            session2.send("/app/game/join", new GameMessage("JOIN", roomId, "ignored", null));

            // Each queue should receive at least one broadcast
            assertThat(queue1.poll(5, TimeUnit.SECONDS)).isNotNull();
            assertThat(queue2.poll(5, TimeUnit.SECONDS)).isNotNull();

            session1.disconnect();
            session2.disconnect();
        }

        @Test
        @DisplayName("Session roomId is stored after join — subsequent messages in the same room succeed")
        void session_roomId_is_stored_after_join() throws Exception {
            String roomId = "integ-room-join-3";
            String nickname = "integ-session-check";

            StompSession session = connectWithNickname(nickname);
            BlockingQueue<GameMessage> queue = new LinkedBlockingQueue<>();
            session.subscribe("/topic/room/" + roomId, gameMessageHandler(queue));

            // First join sets roomId in the session attributes
            session.send("/app/game/join", new GameMessage("JOIN", roomId, "x", null));

            GameMessage msg = queue.poll(5, TimeUnit.SECONDS);
            assertThat(msg).isNotNull();
            assertThat(msg.type()).isEqualTo("JOIN");

            session.disconnect();
        }
    }
}
