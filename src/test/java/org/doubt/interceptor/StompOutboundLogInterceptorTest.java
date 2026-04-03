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
class StompOutboundLogInterceptorTest {

    private StompOutboundLogInterceptor stompOutboundLogInterceptor;

    @Mock
    private MessageChannel messageChannel;

    @BeforeEach
    void setUp() {
        stompOutboundLogInterceptor = new StompOutboundLogInterceptor();
    }

    private Message<byte[]> buildStompMessage(StompCommand command, String destination, byte[] payload) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    @Nested
    @DisplayName("preSend")
    class PreSend {

        @Test
        @DisplayName("MESSAGE 커맨드 — 아웃바운드 로그를 남기고 null이 아닌 동일한 메시지 객체를 반환한다")
        void message_command_logs_and_returns_same_message() {
            byte[] payload = "{\"event\":\"stateUpdate\"}".getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.MESSAGE, "/topic/game/1", payload);

            Message<?> result = stompOutboundLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result, "MESSAGE 커맨드에서 preSend 결과가 null이어서는 안 된다");
            assertSame(message, result, "원본 메시지와 동일한 참조를 반환해야 한다");
        }

        @Test
        @DisplayName("MESSAGE 커맨드 — destination이 null이어도 예외 없이 동일한 메시지를 반환한다")
        void message_command_null_destination_returns_message() {
            byte[] payload = "{}".getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.MESSAGE, null, payload);

            Message<?> result = stompOutboundLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result);
            assertSame(message, result);
        }

        @Test
        @DisplayName("SEND 커맨드 — MESSAGE 분기에 해당하지 않아 예외 없이 그대로 메시지를 반환한다")
        void send_command_passes_through_without_error() {
            byte[] payload = "{\"action\":\"play\"}".getBytes();
            Message<byte[]> message = buildStompMessage(
                    StompCommand.SEND, "/app/game/1", payload);

            Message<?> result = stompOutboundLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result, "SEND 커맨드도 메시지를 반환해야 한다");
            assertSame(message, result);
        }

        @Test
        @DisplayName("CONNECT 커맨드 — MESSAGE 분기에 해당하지 않아 예외 없이 그대로 메시지를 반환한다")
        void connect_command_passes_through_without_error() {
            byte[] payload = new byte[0];
            Message<byte[]> message = buildStompMessage(
                    StompCommand.CONNECT, null, payload);

            Message<?> result = stompOutboundLogInterceptor.preSend(message, messageChannel);

            assertNotNull(result, "CONNECT 커맨드도 메시지를 반환해야 한다");
            assertSame(message, result);
        }
    }
}
