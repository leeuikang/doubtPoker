package org.doubt.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class StompLogInterceptorTest {

    private StompLogInterceptor stompLogInterceptor;

    @Mock
    private MessageChannel messageChannel;

    // Logback ListAppender — 로그 레벨 검증용
    private ListAppender<ILoggingEvent> listAppender;
    private Logger interceptorLogger;

    @BeforeEach
    void setUp() {
        stompLogInterceptor = new StompLogInterceptor();

        // StompLogInterceptor 의 Lombok @Slf4j 로거를 캡처
        interceptorLogger = (Logger) LoggerFactory.getLogger(StompLogInterceptor.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        interceptorLogger.addAppender(listAppender);
        interceptorLogger.setLevel(Level.DEBUG); // DEBUG 레벨까지 캡처
    }

    @AfterEach
    void tearDown() {
        interceptorLogger.detachAppender(listAppender);
    }

    private Message<byte[]> buildStompMessage(StompCommand command, String destination, String sessionId, byte[] payload) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    @Nested
    @DisplayName("preSend")
    class PreSend {

        @Test
        @DisplayName("SEND 커맨드 — byte[] 페이로드를 String으로 변환해 로깅 후 동일한 메시지 객체를 반환한다")
        void send_command_byte_array_payload_returns_same_message() {
            byte[] payload = "{\"action\":\"play\"}".getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SEND, "/app/game/1", "session-abc", payload);

            Message<?> result = stompLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result, "preSend 결과가 null이어서는 안 된다");
            assertSame(message, result, "원본 메시지와 동일한 참조를 반환해야 한다");
        }

        @Test
        @DisplayName("SEND 커맨드 — destination이 null이어도 예외 없이 동일한 메시지를 반환한다")
        void send_command_null_destination_returns_message() {
            byte[] payload = "hello".getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SEND, null, "session-xyz", payload);

            Message<?> result = stompLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result);
            assertSame(message, result);
        }

        @Test
        @DisplayName("SEND 커맨드 — instanceof 분기: byte[] 타입 페이로드는 new String(bytes)로 처리된다")
        void send_command_instanceof_byte_array_branch() {
            // byte[] 인스턴스를 사용 -> (raw instanceof byte[] bytes) 분기 진입
            byte[] payload = "test-payload".getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SEND, "/app/test", "session-inst", payload);

            Message<?> result = stompLogInterceptor.preSend(message, messageChannel);

            // 로깅 경로에서 예외 없이 통과하고 동일한 메시지를 반환해야 한다
            assertNotNull(result);
            assertSame(message, result);
        }

        @Test
        @DisplayName("CONNECT 커맨드 — SEND 분기에 해당하지 않아 그대로 메시지를 반환한다")
        void connect_command_passes_through_without_error() {
            byte[] payload = new byte[0];
            Message<byte[]> message = buildStompMessage(
                    StompCommand.CONNECT, null, "session-conn", payload);

            Message<?> result = stompLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result, "CONNECT 커맨드도 메시지를 반환해야 한다");
            assertSame(message, result);
        }

        @Test
        @DisplayName("DISCONNECT 커맨드 — SEND 분기에 해당하지 않아 그대로 메시지를 반환한다")
        void disconnect_command_passes_through_without_error() {
            byte[] payload = new byte[0];
            Message<byte[]> message = buildStompMessage(
                    StompCommand.DISCONNECT, null, "session-disc", payload);

            Message<?> result = stompLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result);
            assertSame(message, result);
        }

        @Test
        @DisplayName("SUBSCRIBE 커맨드 — SEND 분기에 해당하지 않아 그대로 메시지를 반환한다")
        void subscribe_command_passes_through_without_error() {
            byte[] payload = new byte[0];
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SUBSCRIBE, "/topic/game/1", "session-sub", payload);

            Message<?> result = stompLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result);
            assertSame(message, result);
        }

        @Test
        @DisplayName("SEND to /app/game/action — INFO 로그가 발생하지 않는다 (DEBUG 레벨로 격하)")
        void send_to_game_action_does_not_emit_info_log() {
            byte[] payload = "{\"type\":\"DRAW\"}".getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SEND, "/app/game/action", "session-action", payload);

            stompLogInterceptor.preSend(message, messageChannel);

            List<ILoggingEvent> infoLogs = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .toList();

            assertThat(infoLogs).isEmpty();
        }

        @Test
        @DisplayName("SEND to /app/game/action — DEBUG 로그가 발생한다")
        void send_to_game_action_emits_debug_log() {
            byte[] payload = "{\"type\":\"DRAW\"}".getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SEND, "/app/game/action", "session-action", payload);

            stompLogInterceptor.preSend(message, messageChannel);

            List<ILoggingEvent> debugLogs = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.DEBUG)
                    .toList();

            assertThat(debugLogs).hasSize(1);
            assertThat(debugLogs.get(0).getFormattedMessage()).contains("/app/game/action");
        }

        @Test
        @DisplayName("SEND to /app/game/join — /app/game/ prefix에 해당하므로 DEBUG 로그가 발생하고 페이로드가 포함된다")
        void send_to_game_join_emits_debug_log_with_payload() {
            String payloadJson = "{\"type\":\"JOIN\"}";
            byte[] payload = payloadJson.getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SEND, "/app/game/join", "session-join", payload);

            stompLogInterceptor.preSend(message, messageChannel);

            List<ILoggingEvent> infoLogs = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .toList();
            assertThat(infoLogs).isEmpty();

            List<ILoggingEvent> debugLogs = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.DEBUG)
                    .toList();
            assertThat(debugLogs).hasSize(1);
            assertThat(debugLogs.get(0).getFormattedMessage())
                    .contains("/app/game/join")
                    .contains(payloadJson);
        }

        @Test
        @DisplayName("SEND to /app/chat — /app/game/ prefix 미해당이므로 INFO 로그가 발생하고 페이로드가 포함된다")
        void send_to_non_game_dest_emits_info_log_with_payload() {
            String payloadJson = "{\"message\":\"hello\"}";
            byte[] payload = payloadJson.getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SEND, "/app/chat", "session-chat", payload);

            stompLogInterceptor.preSend(message, messageChannel);

            List<ILoggingEvent> infoLogs = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .toList();
            assertThat(infoLogs).hasSize(1);
            assertThat(infoLogs.get(0).getFormattedMessage())
                    .contains("/app/chat")
                    .contains(payloadJson);
        }
    }
}
