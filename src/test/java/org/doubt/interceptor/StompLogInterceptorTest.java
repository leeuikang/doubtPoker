package org.doubt.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class StompLogInterceptorTest {

    private StompLogInterceptor stompLogInterceptor;

    @Mock
    private MessageChannel messageChannel;

    @BeforeEach
    void setUp() {
        stompLogInterceptor = new StompLogInterceptor();
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
    }
}
