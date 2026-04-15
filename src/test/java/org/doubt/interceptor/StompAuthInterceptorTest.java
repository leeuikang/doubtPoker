package org.doubt.interceptor;

import org.doubt.auth.GuestClaims;
import org.doubt.auth.GuestTokenService;
import org.doubt.auth.NicknameRegistry;
import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StompAuthInterceptor")
class StompAuthInterceptorTest {

    @Mock
    private GuestTokenService guestTokenService;

    @Mock
    private NicknameRegistry nicknameRegistry;

    @Mock
    private MessageChannel messageChannel;

    private StompAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthInterceptor(guestTokenService, nicknameRegistry);
        // CONNECT 외 커맨드 테스트에서는 호출되지 않으므로 lenient 처리
        lenient().when(nicknameRegistry.register(any())).thenReturn(true);
    }

    // ----------------------------------------------------------------
    // 헬퍼: STOMP 메시지 생성
    // ----------------------------------------------------------------
    private Message<byte[]> buildConnectMessage(String authHeaderValue) {
        return buildConnectMessage(authHeaderValue, new HashMap<>());
    }

    private Message<byte[]> buildConnectMessage(String authHeaderValue, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(sessionAttributes);
        if (authHeaderValue != null) {
            accessor.addNativeHeader("Authorization", authHeaderValue);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> buildMessage(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // ----------------------------------------------------------------

    @Nested
    @DisplayName("preSend - CONNECT")
    class PreSendConnect {

        @Test
        @DisplayName("유효한 Bearer 토큰이면 메시지를 그대로 반환하고 세션에 guestId와 nickname을 저장한다")
        void valid_token_passes_through_and_stores_session_attributes() {
            String token = "valid.token";
            GuestClaims claims = new GuestClaims("guest-uuid-123", "홍길동", System.currentTimeMillis() + 3600_000L);
            when(guestTokenService.verify(token)).thenReturn(claims);

            // CSRF 교차 검증: 핸드셰이크 단계에서 저장된 csrfGuestId가 세션 속성에 있어야 함
            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("csrfGuestId", "guest-uuid-123");
            Message<byte[]> message = buildConnectMessage("Bearer " + token, sessionAttrs);
            Message<?> result = interceptor.preSend(message, messageChannel);

            assertThat(result).isNotNull();
            verify(guestTokenService, times(1)).verify(token);

            // 세션 속성 확인
            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            Map<String, Object> attrs = resultAccessor.getSessionAttributes();
            assertThat(attrs).isNotNull();
            assertThat(attrs.get("guestId")).isEqualTo("guest-uuid-123");
            assertThat(attrs.get("nickname")).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("Authorization 헤더가 없으면 UNAUTHORIZED 예외가 발생한다")
        void missing_authorization_header_throws_unauthorized() {
            Message<byte[]> message = buildConnectMessage(null);

            assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(guestTokenService, never()).verify(any());
        }

        @Test
        @DisplayName("Authorization 헤더가 Bearer로 시작하지 않으면 UNAUTHORIZED 예외가 발생한다")
        void non_bearer_authorization_header_throws_unauthorized() {
            Message<byte[]> message = buildConnectMessage("Basic dXNlcjpwYXNz");

            assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(guestTokenService, never()).verify(any());
        }

        @Test
        @DisplayName("토큰 검증이 TOKEN_EXPIRED를 던지면 해당 예외가 그대로 전파된다")
        void token_verification_throwing_token_expired_propagates() {
            String token = "expired.token";
            when(guestTokenService.verify(token))
                    .thenThrow(new GameException(ErrorCode.TOKEN_EXPIRED));

            Message<byte[]> message = buildConnectMessage("Bearer " + token);

            assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("이미 사용 중인 닉네임이면 DUPLICATE_NICKNAME 예외가 발생한다")
        void duplicate_nickname_throws_exception() {
            String token = "valid.token";
            GuestClaims claims = new GuestClaims("guest-uuid", "중복닉네임", System.currentTimeMillis() + 3600_000L);
            when(guestTokenService.verify(token)).thenReturn(claims);
            when(nicknameRegistry.register("중복닉네임")).thenReturn(false); // 이미 사용 중

            Message<byte[]> message = buildConnectMessage("Bearer " + token);

            assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.DUPLICATE_NICKNAME));
        }

        @Test
        @DisplayName("Authorization 헤더가 빈 문자열이면 UNAUTHORIZED 예외가 발생한다")
        void empty_authorization_header_throws_unauthorized() {
            Message<byte[]> message = buildConnectMessage("");

            assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(guestTokenService, never()).verify(any());
        }
    }

    @Nested
    @DisplayName("preSend - CONNECT 외 커맨드")
    class PreSendNonConnect {

        @Test
        @DisplayName("SEND 커맨드는 verify 호출 없이 메시지를 그대로 반환한다")
        void send_command_passes_through_without_verify() {
            Message<byte[]> message = buildMessage(StompCommand.SEND);

            Message<?> result = interceptor.preSend(message, messageChannel);

            assertThat(result).isSameAs(message);
            verify(guestTokenService, never()).verify(any());
        }

        @Test
        @DisplayName("SUBSCRIBE 커맨드는 verify 호출 없이 메시지를 그대로 반환한다")
        void subscribe_command_passes_through_without_verify() {
            Message<byte[]> message = buildMessage(StompCommand.SUBSCRIBE);

            Message<?> result = interceptor.preSend(message, messageChannel);

            assertThat(result).isSameAs(message);
            verify(guestTokenService, never()).verify(any());
        }

        @Test
        @DisplayName("DISCONNECT 커맨드는 verify 호출 없이 메시지를 그대로 반환한다")
        void disconnect_command_passes_through_without_verify() {
            Message<byte[]> message = buildMessage(StompCommand.DISCONNECT);

            Message<?> result = interceptor.preSend(message, messageChannel);

            assertThat(result).isSameAs(message);
            verify(guestTokenService, never()).verify(any());
        }
    }

    // ----------------------------------------------------------------
    // R2-I1: CSRF 실패 시 닉네임 레지스트리 누수 방지 (보상 처리)
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("R2-I1 — CSRF 실패 시 NicknameRegistry 누수 방지")
    class CsrfFailureNicknameRelease {

        @Test
        @DisplayName("CSRF guestId 불일치 시 UNAUTHORIZED가 발생하고 nickname이 레지스트리에서 해제된다")
        void csrf_mismatch_throws_unauthorized_and_releases_nickname() {
            String token = "valid.token";
            GuestClaims claims = new GuestClaims("auth-guest-id", "테스터", System.currentTimeMillis() + 3600_000L);
            when(guestTokenService.verify(token)).thenReturn(claims);
            when(nicknameRegistry.register("테스터")).thenReturn(true);

            // 핸드셰이크 단계에서 저장된 csrfGuestId가 토큰의 guestId와 다름
            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("csrfGuestId", "different-guest-id");
            Message<byte[]> message = buildConnectMessage("Bearer " + token, sessionAttrs);

            assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            // 닉네임이 등록된 뒤 예외 발생으로 반드시 해제되어야 한다
            verify(nicknameRegistry, times(1)).register("테스터");
            verify(nicknameRegistry, times(1)).release("테스터");
        }

        @Test
        @DisplayName("CSRF csrfGuestId 속성이 null이면 UNAUTHORIZED가 발생하고 nickname이 해제된다")
        void csrf_guestid_null_throws_unauthorized_and_releases_nickname() {
            String token = "valid.token";
            GuestClaims claims = new GuestClaims("auth-guest-id", "테스터2", System.currentTimeMillis() + 3600_000L);
            when(guestTokenService.verify(token)).thenReturn(claims);
            when(nicknameRegistry.register("테스터2")).thenReturn(true);

            // csrfGuestId 키 자체가 없는 세션 속성
            Map<String, Object> sessionAttrs = new HashMap<>();
            Message<byte[]> message = buildConnectMessage("Bearer " + token, sessionAttrs);

            assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(nicknameRegistry, times(1)).register("테스터2");
            verify(nicknameRegistry, times(1)).release("테스터2");
        }

        @Test
        @DisplayName("CSRF 일치 시 nickname이 레지스트리에 등록된 채로 유지되고 release는 호출되지 않는다")
        void csrf_match_nickname_remains_registered() {
            String token = "valid.token";
            GuestClaims claims = new GuestClaims("correct-guest-id", "정상유저", System.currentTimeMillis() + 3600_000L);
            when(guestTokenService.verify(token)).thenReturn(claims);
            when(nicknameRegistry.register("정상유저")).thenReturn(true);

            Map<String, Object> sessionAttrs = new HashMap<>();
            sessionAttrs.put("csrfGuestId", "correct-guest-id");
            Message<byte[]> message = buildConnectMessage("Bearer " + token, sessionAttrs);

            Message<?> result = interceptor.preSend(message, messageChannel);

            assertThat(result).isNotNull();
            verify(nicknameRegistry, times(1)).register("정상유저");
            // release는 호출되어서는 안 된다
            verify(nicknameRegistry, never()).release(any());
        }

        @Test
        @DisplayName("sessionAttributes가 null(NPE)이면 nickname이 레지스트리에서 해제된다")
        void session_attributes_null_npe_releases_nickname() {
            String token = "valid.token";
            GuestClaims claims = new GuestClaims("some-guest-id", "NPE유저", System.currentTimeMillis() + 3600_000L);
            when(guestTokenService.verify(token)).thenReturn(claims);
            when(nicknameRegistry.register("NPE유저")).thenReturn(true);

            // sessionAttributes를 null로 설정 → Objects.requireNonNull이 NullPointerException 발생
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setSessionAttributes(null);
            accessor.addNativeHeader("Authorization", "Bearer " + token);
            accessor.setLeaveMutable(true);
            Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                    .isInstanceOf(RuntimeException.class);

            verify(nicknameRegistry, times(1)).register("NPE유저");
            verify(nicknameRegistry, times(1)).release("NPE유저");
        }
    }
}
