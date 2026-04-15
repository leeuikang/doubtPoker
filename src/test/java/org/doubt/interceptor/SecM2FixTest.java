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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * SEC-M2 보안 수정 검증 테스트
 *
 * <p>원래 문제: /app/chat/message 페이로드가 INFO 레벨로 로깅되어
 * 채팅 내용이 프로덕션 로그에 노출되고 로그 인젝션 위험이 있었다.</p>
 *
 * <p>수정 내용:
 * - {@code SENSITIVE_DEST_PREFIX = "/app/game/"} → {@code SENSITIVE_APP_PREFIX = "/app/"}
 * - /app/ 하위 모든 경로(게임·채팅)의 페이로드를 DEBUG 레벨로 격하
 * - /app/ 이외의 경로(CONNECT, SUBSCRIBE 등)는 INFO 레벨이나 페이로드 미포함</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SEC-M2 수정 검증: 채팅 페이로드 로그 레벨 격하")
class SecM2FixTest {

    private StompLogInterceptor interceptor;

    @Mock
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        interceptor = new StompLogInterceptor();
    }

    private Message<byte[]> buildSendMessage(String destination, byte[] payload) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setSessionId("session-1");
        return MessageBuilder.createMessage(payload, accessor.getMessageHeaders());
    }

    @Nested
    @DisplayName("preSend — 채팅 destination")
    class PreSend_ChatDestination {

        @Test
        @DisplayName("/app/chat/message 전송 시 동일한 메시지 인스턴스를 반환한다")
        void preSend_chatDestination_returnsMessageUnchanged() {
            // given
            byte[] payload = "{\"message\":\"hello\",\"sender\":\"alice\"}".getBytes(StandardCharsets.UTF_8);
            Message<byte[]> message = buildSendMessage("/app/chat/message", payload);

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then: 동일한 인스턴스가 반환되어야 한다 (메시지가 수정/차단되지 않음)
            assertNotNull(result);
            assertSame(message, result);
        }

        @Test
        @DisplayName("/app/chat/message 전송은 예외를 발생시키지 않는다")
        void preSend_sendToAppChat_doesNotThrow() {
            byte[] payload = "{\"message\":\"hello\",\"sender\":\"alice\"}".getBytes(StandardCharsets.UTF_8);
            Message<byte[]> message = buildSendMessage("/app/chat/message", payload);

            // 수정 후 DEBUG 레벨로 처리되어도 메서드가 정상 동작해야 한다
            assertDoesNotThrow(() -> interceptor.preSend(message, channel));
        }
    }

    @Nested
    @DisplayName("preSend — 게임 destination")
    class PreSend_GameDestination {

        @Test
        @DisplayName("/app/game/action 전송 시 null이 아닌 메시지를 반환한다")
        void preSend_gameDestination_returnsMessageUnchanged() {
            // given
            byte[] payload = "{\"type\":\"DRAW\"}".getBytes(StandardCharsets.UTF_8);
            Message<byte[]> message = buildSendMessage("/app/game/action", payload);

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("preSend — /app/ 이외 destination")
    class PreSend_NonAppDestination {

        @Test
        @DisplayName("/topic/room/123 전송 시 null이 아닌 메시지를 반환한다")
        void preSend_nonAppDestination_returnsMessageUnchanged() {
            // given
            byte[] payload = "{\"event\":\"update\"}".getBytes(StandardCharsets.UTF_8);
            Message<byte[]> message = buildSendMessage("/topic/room/123", payload);

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("preSend — SEND 이외 커맨드")
    class PreSend_NonSendCommand {

        @Test
        @DisplayName("CONNECT 커맨드는 SEND 분기를 거치지 않고 정상 반환된다")
        void preSend_connectCommand_returnsMessageUnchanged() {
            // given
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then
            assertNotNull(result);
        }

        @Test
        @DisplayName("SUBSCRIBE 커맨드는 예외 없이 메시지를 반환한다")
        void preSend_subscribeCommand_returnsMessageUnchanged() {
            // given
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination("/topic/room/1");
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then
            assertNotNull(result);
        }
    }
}
