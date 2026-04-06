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
        when(nicknameRegistry.register(any())).thenReturn(true);
    }

    // ----------------------------------------------------------------
    // 헬퍼: STOMP 메시지 생성
    // ----------------------------------------------------------------
    private Message<byte[]> buildConnectMessage(String authHeaderValue) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(new HashMap<>());
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
            GuestClaims claims = new GuestClaims("guest-uuid-123", "홍길동");
            when(guestTokenService.verify(token)).thenReturn(claims);

            Message<byte[]> message = buildConnectMessage("Bearer " + token);
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
            GuestClaims claims = new GuestClaims("guest-uuid", "중복닉네임");
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
}
