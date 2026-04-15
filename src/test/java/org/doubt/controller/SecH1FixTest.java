package org.doubt.controller;

import org.doubt.auth.CsrfTokenResponse;
import org.doubt.auth.GuestClaims;
import org.doubt.auth.GuestTokenService;
import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-H1 보안 수정 검증 테스트
 *
 * <p>수정 전: 임의의 guestId 파라미터로 CSRF 토큰 발급 가능 (인증 우회)
 * 수정 후: Authorization Bearer 토큰 검증 후 클레임의 guestId로만 발급</p>
 *
 * <p>SEC-I1 수정 반영: issueCsrfToken이 expiresAt 파라미터를 받아 auth 만료 시각에 연동.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SEC-H1: CSRF 토큰 발급 엔드포인트 보안 수정 검증")
class SecH1FixTest {

    @Mock
    private GuestTokenService guestTokenService;

    @InjectMocks
    private GuestAuthController guestAuthController;

    private static final long FUTURE_EXPIRY = System.currentTimeMillis() + 3600_000L;

    @Nested
    @DisplayName("issueCsrfToken - 정상 케이스")
    class IssueCsrfToken_Success {

        @Test
        @DisplayName("유효한 Bearer 토큰으로 CSRF 토큰이 정상 발급된다")
        void issueCsrfToken_withValidBearerToken_returnsCsrfToken() {
            // given
            GuestClaims claims = new GuestClaims("guest-123", "testNick", FUTURE_EXPIRY);
            when(guestTokenService.verify("valid-token")).thenReturn(claims);
            when(guestTokenService.issueCsrfToken(eq("guest-123"), anyLong())).thenReturn("csrf-token-xyz");

            // when
            CsrfTokenResponse response = guestAuthController.issueCsrfToken("Bearer valid-token");

            // then
            assertThat(response.csrfToken()).isEqualTo("csrf-token-xyz");
            verify(guestTokenService).verify("valid-token");
            verify(guestTokenService).issueCsrfToken(eq("guest-123"), eq(FUTURE_EXPIRY));
        }
    }

    @Nested
    @DisplayName("issueCsrfToken - 헤더 형식 오류")
    class IssueCsrfToken_InvalidHeader {

        @Test
        @DisplayName("Bearer 접두사 없는 헤더는 UNAUTHORIZED 예외를 던진다")
        void issueCsrfToken_withMissingBearerPrefix_throwsUnauthorized() {
            // when & then
            assertThatThrownBy(() ->
                    guestAuthController.issueCsrfToken("invalid-header-no-bearer")
            )
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(guestTokenService, never()).verify(anyString());
        }

        @Test
        @DisplayName("null 헤더는 UNAUTHORIZED 예외를 던진다")
        void issueCsrfToken_withNullHeader_throwsUnauthorized() {
            // when & then
            assertThatThrownBy(() ->
                    guestAuthController.issueCsrfToken(null)
            )
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }
    }

    @Nested
    @DisplayName("issueCsrfToken - 토큰 검증 실패")
    class IssueCsrfToken_TokenVerifyFail {

        @Test
        @DisplayName("만료된 토큰은 TOKEN_EXPIRED 예외를 전파하고 CSRF 토큰 발급을 차단한다")
        void issueCsrfToken_withExpiredToken_propagatesTokenExpired() {
            // given
            when(guestTokenService.verify(any()))
                    .thenThrow(new GameException(ErrorCode.TOKEN_EXPIRED));

            // when & then
            assertThatThrownBy(() ->
                    guestAuthController.issueCsrfToken("Bearer expired-token")
            )
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_EXPIRED));

            verify(guestTokenService, never()).issueCsrfToken(anyString(), anyLong());
        }

        @Test
        @DisplayName("위조된 토큰은 TOKEN_INVALID 예외를 전파한다")
        void issueCsrfToken_withInvalidToken_propagatesTokenInvalid() {
            // given
            when(guestTokenService.verify(any()))
                    .thenThrow(new GameException(ErrorCode.TOKEN_INVALID));

            // when & then
            assertThatThrownBy(() ->
                    guestAuthController.issueCsrfToken("Bearer forged-token")
            )
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_INVALID));
        }
    }

    @Nested
    @DisplayName("issueCsrfToken - 핵심 보안 검증")
    class IssueCsrfToken_SecurityGuarantee {

        @Test
        @DisplayName("[핵심 보안] CSRF 토큰 발급 시 외부 입력이 아닌 검증된 클레임의 guestId를 사용한다")
        void issueCsrfToken_usesGuestIdFromClaims_notFromRequest() {
            // given
            GuestClaims claims = new GuestClaims("real-guest-id", "nick", FUTURE_EXPIRY);
            when(guestTokenService.verify(any())).thenReturn(claims);
            when(guestTokenService.issueCsrfToken(eq("real-guest-id"), anyLong())).thenReturn("csrf-token");

            // when
            guestAuthController.issueCsrfToken("Bearer some-token");

            // then: 검증된 클레임의 guestId("real-guest-id")로만 호출되어야 한다
            verify(guestTokenService).issueCsrfToken(eq("real-guest-id"), eq(FUTURE_EXPIRY));
        }

        @Test
        @DisplayName("[SEC-I1] CSRF 토큰이 auth 토큰과 동일한 expiresAt로 발급된다")
        void csrf_token_uses_auth_token_expiry() {
            // given
            long authExpiresAt = System.currentTimeMillis() + 1800_000L; // 30분 후
            GuestClaims claims = new GuestClaims("guest-abc", "tester", authExpiresAt);
            when(guestTokenService.verify(any())).thenReturn(claims);
            when(guestTokenService.issueCsrfToken(eq("guest-abc"), eq(authExpiresAt))).thenReturn("csrf-xyz");

            // when
            guestAuthController.issueCsrfToken("Bearer some-token");

            // then: auth expiresAt이 그대로 CSRF 발급에 전달된다
            verify(guestTokenService).issueCsrfToken(eq("guest-abc"), eq(authExpiresAt));
        }
    }
}
