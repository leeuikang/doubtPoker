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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-M3 보안 수정 검증 테스트
 *
 * <p>원래 문제: /app/game/ 경로에 레이트 리밋이 없어 공격자가 초당 수백 건을 전송하여
 * synchronized(room) 스레드 경합 및 게임 상태 무결성 위협이 발생했다.</p>
 *
 * <p>수정 내용: {@link GameRateLimitInterceptor} 생성 — /app/game/ 프리픽스 경로에 대해
 * 세션별 분당 최대 {@value GameRateLimitInterceptor#MAX_ACTIONS_PER_WINDOW}건 제한.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SEC-M3 수정 검증: 게임 액션 레이트 리밋")
class SecM3FixTest {

    private GameRateLimitInterceptor interceptor;

    @Mock
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        interceptor = new GameRateLimitInterceptor();
    }

    // --- 메시지 생성 헬퍼 ---

    private Message<?> createSendMessage(String dest, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(dest);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> createDisconnectMessage(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // ==================== 정상 케이스 ====================

    @Nested
    @DisplayName("한도 이하 게임 액션은 통과된다")
    class UnderLimit {

        @Test
        @DisplayName("/app/game/action 으로 첫 전송 시 메시지가 반환된다")
        void preSend_gameActionDestination_allowsMessageUnderLimit() {
            // given
            Message<?> message = createSendMessage("/app/game/action", "s1");

            // when
            Message<?> result = interceptor.preSend(message, channel);

            // then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("정확히 MAX_ACTIONS_PER_WINDOW(60)번 전송 시 모두 통과된다")
        void preSend_withinLimit_allowsAllMessages() {
            // given
            String sessionId = "s1";
            Message<?> lastResult = null;

            // when
            for (int i = 0; i < GameRateLimitInterceptor.MAX_ACTIONS_PER_WINDOW; i++) {
                Message<?> message = createSendMessage("/app/game/action", sessionId);
                lastResult = interceptor.preSend(message, channel);
            }

            // then: 60번째 메시지도 통과되어야 한다
            assertThat(lastResult).isNotNull();
        }
    }

    // ==================== 한도 초과 차단 ====================

    @Nested
    @DisplayName("한도 초과 게임 액션은 차단된다")
    class ExceedsLimit {

        @Test
        @DisplayName("61번째 메시지(MAX_ACTIONS_PER_WINDOW + 1)는 null을 반환하여 차단된다")
        void preSend_exceedsLimit_dropsMessage() {
            // given
            String sessionId = "s1";

            // 60개 소진
            for (int i = 0; i < GameRateLimitInterceptor.MAX_ACTIONS_PER_WINDOW; i++) {
                Message<?> msg = createSendMessage("/app/game/action", sessionId);
                interceptor.preSend(msg, channel);
            }

            // when: 61번째 시도
            Message<?> sixtyFirst = createSendMessage("/app/game/action", sessionId);
            Message<?> result = interceptor.preSend(sixtyFirst, channel);

            // then
            assertThat(result).isNull();
        }
    }

    // ==================== 대상 외 경로 필터링 ====================

    @Nested
    @DisplayName("/app/game/ 이외 경로는 게임 레이트 리밋을 적용하지 않는다")
    class NonGameDestination {

        @Test
        @DisplayName("/app/chat/message 로 100번 전송해도 차단되지 않는다")
        void preSend_nonGameDestination_isNotRateLimited() {
            // given
            String sessionId = "s1";

            // when
            Message<?> lastResult = null;
            for (int i = 0; i < 100; i++) {
                Message<?> msg = createSendMessage("/app/chat/message", sessionId);
                lastResult = interceptor.preSend(msg, channel);
            }

            // then: 100번 모두 통과되어야 한다
            assertThat(lastResult).isNotNull();
        }
    }

    // ==================== 세션 독립성 ====================

    @Nested
    @DisplayName("세션 간 레이트 리밋은 독립적으로 동작한다")
    class SessionIsolation {

        @Test
        @DisplayName("세션 s1이 한도를 소진해도 세션 s2의 메시지는 차단되지 않는다")
        void preSend_differentSessions_haveSeparateLimits() {
            // given: s1이 60건 모두 소진
            for (int i = 0; i < GameRateLimitInterceptor.MAX_ACTIONS_PER_WINDOW; i++) {
                Message<?> msg = createSendMessage("/app/game/action", "s1");
                interceptor.preSend(msg, channel);
            }

            // s1은 차단됨을 확인
            assertThat(interceptor.preSend(createSendMessage("/app/game/action", "s1"), channel))
                    .isNull();

            // when: s2의 첫 번째 액션
            Message<?> s2Message = createSendMessage("/app/game/action", "s2");
            Message<?> result = interceptor.preSend(s2Message, channel);

            // then: s2는 독립적으로 통과되어야 한다
            assertThat(result).isNotNull();
        }
    }

    // ==================== DISCONNECT 시 버킷 정리 ====================

    @Nested
    @DisplayName("DISCONNECT 시 세션 버킷이 정리된다")
    class DisconnectCleanup {

        @Test
        @DisplayName("DISCONNECT 후 동일 세션의 액션은 새 윈도우로 통과된다")
        void afterSendCompletion_disconnect_cleansBucket() {
            // given: s1이 60건 소진 → 차단 상태
            String sessionId = "s1";
            for (int i = 0; i < GameRateLimitInterceptor.MAX_ACTIONS_PER_WINDOW; i++) {
                interceptor.preSend(createSendMessage("/app/game/action", sessionId), channel);
            }
            assertThat(interceptor.preSend(createSendMessage("/app/game/action", sessionId), channel))
                    .isNull();

            // when: DISCONNECT로 버킷 정리
            Message<?> disconnectMessage = createDisconnectMessage(sessionId);
            interceptor.afterSendCompletion(disconnectMessage, channel, true, null);

            // then: 버킷이 새로 생성되어 다시 통과되어야 한다
            Message<?> result = interceptor.preSend(createSendMessage("/app/game/action", sessionId), channel);
            assertThat(result).isNotNull();
        }
    }

    // ==================== 프리픽스 검증 ====================

    @Nested
    @DisplayName("/app/game/ 프리픽스 하위 모든 경로에 레이트 리밋이 적용된다")
    class PrefixCoverage {

        @Test
        @DisplayName("/app/game/round/start 경로도 61번째에 차단된다")
        void preSend_gameRoundStart_isRateLimited() {
            // given
            String sessionId = "s1";
            String dest = "/app/game/round/start";

            // 60개 소진
            for (int i = 0; i < GameRateLimitInterceptor.MAX_ACTIONS_PER_WINDOW; i++) {
                interceptor.preSend(createSendMessage(dest, sessionId), channel);
            }

            // when: 61번째 시도
            Message<?> result = interceptor.preSend(createSendMessage(dest, sessionId), channel);

            // then
            assertThat(result).isNull();
        }
    }
}
