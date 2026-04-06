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

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatRateLimitInterceptor 단위 테스트 (L-3)
 * - 세션별 분당 최대 20건 제한 검증
 * - /app/chat/message 목적지에만 제한 적용 검증
 * - DISCONNECT 시 버킷 정리 검증
 */
@ExtendWith(MockitoExtension.class)
class ChatRateLimitInterceptorTest {

    private ChatRateLimitInterceptor interceptor;

    @Mock
    private MessageChannel messageChannel;

    @BeforeEach
    void setUp() {
        interceptor = new ChatRateLimitInterceptor();
    }

    // --- 메시지 생성 헬퍼 ---

    private Message<byte[]> buildMessage(StompCommand command, String destination, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // --- 내부 버킷 맵 접근 헬퍼 ---

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Object> getBuckets() throws Exception {
        Field bucketsField = ChatRateLimitInterceptor.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) bucketsField.get(interceptor);
    }

    // ==================== 정상 케이스: 레이트 리밋 통과 ====================

    @Nested
    @DisplayName("레이트 리밋 통과 조건")
    class RateLimitPass {

        @Test
        @DisplayName("첫 번째 채팅 메시지는 통과한다")
        void first_chat_message_passes_through() {
            Message<byte[]> message = buildMessage(StompCommand.SEND, "/app/chat/message", "session-1");

            Message<?> result = interceptor.preSend(message, messageChannel);

            assertNotNull(result, "첫 메시지는 null이 아니어야 한다");
        }

        @Test
        @DisplayName("윈도우 내 20번째 메시지까지는 모두 통과한다")
        void messages_up_to_limit_pass_through() {
            String sessionId = "session-limit";

            Message<?> lastResult = null;
            for (int i = 0; i < ChatRateLimitInterceptor.MAX_MESSAGES_PER_WINDOW; i++) {
                Message<byte[]> message = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
                lastResult = interceptor.preSend(message, messageChannel);
            }

            assertNotNull(lastResult, "20번째 메시지는 통과해야 한다");
        }
    }

    // ==================== 경계 케이스: 21번째 메시지 차단 ====================

    @Nested
    @DisplayName("레이트 리밋 초과 차단")
    class RateLimitExceeded {

        @Test
        @DisplayName("21번째 메시지는 null을 반환하여 차단된다")
        void twenty_first_message_is_dropped() {
            String sessionId = "session-over";

            // 20개 소진
            for (int i = 0; i < ChatRateLimitInterceptor.MAX_MESSAGES_PER_WINDOW; i++) {
                Message<byte[]> msg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
                interceptor.preSend(msg, messageChannel);
            }

            // 21번째 시도
            Message<byte[]> twentyFirst = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
            Message<?> result = interceptor.preSend(twentyFirst, messageChannel);

            assertNull(result, "21번째 메시지는 null(차단)이어야 한다");
        }

        @Test
        @DisplayName("차단 이후 추가 메시지도 계속 차단된다")
        void messages_after_limit_keep_being_dropped() {
            String sessionId = "session-continued";

            for (int i = 0; i < ChatRateLimitInterceptor.MAX_MESSAGES_PER_WINDOW + 3; i++) {
                Message<byte[]> msg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
                interceptor.preSend(msg, messageChannel);
            }

            // 24번째 메시지도 차단되어야 한다
            Message<byte[]> extra = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
            Message<?> result = interceptor.preSend(extra, messageChannel);

            assertNull(result, "한도를 초과한 메시지는 계속 차단되어야 한다");
        }
    }

    // ==================== 윈도우 리셋 ====================

    @Nested
    @DisplayName("윈도우 리셋 후 재허용")
    class WindowReset {

        @Test
        @DisplayName("윈도우 시작 시간을 직접 조작해 60초 경과 후 메시지가 다시 통과된다")
        void after_window_reset_messages_pass_again() throws Exception {
            String sessionId = "session-reset";

            // 20개 소진하여 차단 상태로 만든다
            for (int i = 0; i < ChatRateLimitInterceptor.MAX_MESSAGES_PER_WINDOW; i++) {
                Message<byte[]> msg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
                interceptor.preSend(msg, messageChannel);
            }

            // 21번째는 차단 확인
            Message<byte[]> blockedMsg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
            assertNull(interceptor.preSend(blockedMsg, messageChannel), "차단 확인");

            // 내부 버킷의 windowStart를 61초 전으로 조작하여 윈도우 만료를 시뮬레이션한다
            ConcurrentHashMap<String, Object> buckets = getBuckets();
            Object bucket = buckets.get(sessionId);
            assertNotNull(bucket, "버킷이 존재해야 한다");

            Field windowStartField = bucket.getClass().getDeclaredField("windowStart");
            windowStartField.setAccessible(true);
            long expiredTime = System.currentTimeMillis() - 61_000L;
            windowStartField.set(bucket, expiredTime);

            // 윈도우가 리셋되어 메시지가 다시 통과되어야 한다
            Message<byte[]> freshMsg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
            Message<?> result = interceptor.preSend(freshMsg, messageChannel);

            assertNotNull(result, "윈도우 리셋 후 첫 메시지는 통과해야 한다");
        }
    }

    // ==================== 목적지 필터링 ====================

    @Nested
    @DisplayName("채팅 외 목적지는 레이트 리밋을 적용하지 않는다")
    class DestinationFilter {

        @Test
        @DisplayName("/app/game/action 목적지는 레이트 리밋이 적용되지 않는다")
        void game_action_destination_is_not_rate_limited() {
            String sessionId = "session-game";

            // /app/game/action 으로 100번 전송해도 차단되지 않아야 한다
            Message<?> lastResult = null;
            for (int i = 0; i < 100; i++) {
                Message<byte[]> msg = buildMessage(StompCommand.SEND, "/app/game/action", sessionId);
                lastResult = interceptor.preSend(msg, messageChannel);
            }

            assertNotNull(lastResult, "/app/game/action은 100번 전송해도 차단되면 안 된다");
        }

        @Test
        @DisplayName("/app/room/join 목적지는 레이트 리밋이 적용되지 않는다")
        void room_join_destination_is_not_rate_limited() {
            String sessionId = "session-join";

            Message<byte[]> msg = buildMessage(StompCommand.SEND, "/app/room/join", sessionId);
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertNotNull(result, "/app/room/join은 레이트 리밋 대상이 아니다");
        }

        @Test
        @DisplayName("목적지가 null이면 레이트 리밋이 적용되지 않는다")
        void null_destination_is_not_rate_limited() {
            String sessionId = "session-null-dest";

            Message<byte[]> msg = buildMessage(StompCommand.SEND, null, sessionId);
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertNotNull(result, "목적지가 null이면 통과되어야 한다");
        }
    }

    // ==================== STOMP 커맨드 필터링 ====================

    @Nested
    @DisplayName("SEND가 아닌 STOMP 커맨드는 레이트 리밋을 적용하지 않는다")
    class CommandFilter {

        @Test
        @DisplayName("CONNECT 커맨드는 레이트 리밋이 적용되지 않는다")
        void connect_command_is_not_rate_limited() {
            Message<byte[]> msg = buildMessage(StompCommand.CONNECT, null, "session-conn");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertNotNull(result, "CONNECT 커맨드는 통과되어야 한다");
        }

        @Test
        @DisplayName("DISCONNECT 커맨드는 레이트 리밋이 적용되지 않는다")
        void disconnect_command_is_not_rate_limited() {
            Message<byte[]> msg = buildMessage(StompCommand.DISCONNECT, null, "session-disc");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertNotNull(result, "DISCONNECT 커맨드는 통과되어야 한다");
        }

        @Test
        @DisplayName("SUBSCRIBE 커맨드는 레이트 리밋이 적용되지 않는다")
        void subscribe_command_is_not_rate_limited() {
            Message<byte[]> msg = buildMessage(StompCommand.SUBSCRIBE, "/topic/room/1", "session-sub");
            Message<?> result = interceptor.preSend(msg, messageChannel);

            assertNotNull(result, "SUBSCRIBE 커맨드는 통과되어야 한다");
        }
    }

    // ==================== DISCONNECT 시 버킷 정리 ====================

    @Nested
    @DisplayName("DISCONNECT 시 세션 버킷 정리")
    class DisconnectCleanup {

        @Test
        @DisplayName("DISCONNECT afterSendCompletion 호출 시 해당 세션의 버킷이 제거된다")
        void disconnect_removes_session_bucket() throws Exception {
            String sessionId = "session-cleanup";

            // 채팅 메시지를 전송하여 버킷을 생성한다
            Message<byte[]> chatMsg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
            interceptor.preSend(chatMsg, messageChannel);

            ConcurrentHashMap<String, Object> buckets = getBuckets();
            assertTrue(buckets.containsKey(sessionId), "메시지 전송 후 버킷이 생성되어야 한다");

            // DISCONNECT afterSendCompletion 호출
            Message<byte[]> disconnectMsg = buildMessage(StompCommand.DISCONNECT, null, sessionId);
            interceptor.afterSendCompletion(disconnectMsg, messageChannel, true, null);

            assertFalse(buckets.containsKey(sessionId), "DISCONNECT 후 버킷이 제거되어야 한다");
        }

        @Test
        @DisplayName("DISCONNECT 후 동일 세션이 재연결하면 새 윈도우로 메시지가 통과된다")
        void reconnected_session_gets_fresh_bucket() throws Exception {
            String sessionId = "session-reconnect";

            // 20개 소진 후 차단 상태로 만든다
            for (int i = 0; i < ChatRateLimitInterceptor.MAX_MESSAGES_PER_WINDOW; i++) {
                Message<byte[]> msg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
                interceptor.preSend(msg, messageChannel);
            }
            Message<byte[]> blockedMsg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
            assertNull(interceptor.preSend(blockedMsg, messageChannel), "차단 확인");

            // DISCONNECT로 버킷 제거
            Message<byte[]> disconnectMsg = buildMessage(StompCommand.DISCONNECT, null, sessionId);
            interceptor.afterSendCompletion(disconnectMsg, messageChannel, true, null);

            // 재연결 후 첫 메시지는 통과되어야 한다
            Message<byte[]> freshMsg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
            Message<?> result = interceptor.preSend(freshMsg, messageChannel);

            assertNotNull(result, "DISCONNECT 후 재연결 시 첫 메시지는 통과되어야 한다");
        }

        @Test
        @DisplayName("SEND afterSendCompletion 호출 시 버킷이 제거되지 않는다")
        void send_after_send_completion_does_not_remove_bucket() throws Exception {
            String sessionId = "session-send-complete";

            // 버킷 생성
            Message<byte[]> chatMsg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionId);
            interceptor.preSend(chatMsg, messageChannel);

            // SEND afterSendCompletion은 버킷을 건드리지 않아야 한다
            interceptor.afterSendCompletion(chatMsg, messageChannel, true, null);

            ConcurrentHashMap<String, Object> buckets = getBuckets();
            assertTrue(buckets.containsKey(sessionId), "SEND afterSendCompletion 후에는 버킷이 유지되어야 한다");
        }
    }

    // ==================== 세션 독립성 ====================

    @Nested
    @DisplayName("세션 간 레이트 리밋은 독립적으로 동작한다")
    class SessionIsolation {

        @Test
        @DisplayName("세션 A가 한도에 도달해도 세션 B는 차단되지 않는다")
        void different_sessions_are_independent() {
            String sessionA = "session-a";
            String sessionB = "session-b";

            // 세션 A를 한도까지 소진한다
            for (int i = 0; i < ChatRateLimitInterceptor.MAX_MESSAGES_PER_WINDOW; i++) {
                Message<byte[]> msg = buildMessage(StompCommand.SEND, "/app/chat/message", sessionA);
                interceptor.preSend(msg, messageChannel);
            }

            // 세션 A의 21번째는 차단되어야 한다
            Message<byte[]> aOverLimit = buildMessage(StompCommand.SEND, "/app/chat/message", sessionA);
            assertNull(interceptor.preSend(aOverLimit, messageChannel), "세션 A는 차단되어야 한다");

            // 세션 B의 첫 메시지는 통과되어야 한다
            Message<byte[]> bFirst = buildMessage(StompCommand.SEND, "/app/chat/message", sessionB);
            Message<?> bResult = interceptor.preSend(bFirst, messageChannel);

            assertNotNull(bResult, "세션 B는 세션 A와 독립적으로 통과되어야 한다");
        }
    }
}
