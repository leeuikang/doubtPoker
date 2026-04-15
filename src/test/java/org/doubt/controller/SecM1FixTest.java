package org.doubt.controller;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-M1 보안 취약점 수정 검증 테스트
 *
 * <p>원래 취약점(SEC-M1): GET /auth/csrf 가 임의의 guestId 파라미터를 길이·형식 검증 없이
 * 그대로 받아 DoS 공격 벡터로 악용 가능했다.</p>
 *
 * <p>해결 방식(SEC-H1): 엔드포인트 시그니처를 {@code @RequestParam guestId}에서
 * {@code @RequestHeader Authorization}으로 변경하여 Bearer 토큰 검증 후
 * 클레임의 guestId를 사용한다. 외부에서 직접 guestId를 지정하는 경로 자체가 제거되었다.</p>
 *
 * <p>SEC-I1 수정 반영: issueCsrfToken이 expiresAt 파라미터를 받아 auth 만료 시각에 연동.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SEC-M1 수정 검증: 임의 guestId 입력 경로 제거")
class SecM1FixTest {

    @Mock
    private GuestTokenService guestTokenService;

    @InjectMocks
    private GuestAuthController guestAuthController;

    private static final long FUTURE_EXPIRY = System.currentTimeMillis() + 3600_000L;

    @Nested
    @DisplayName("issueCsrfToken — 원래 공격 벡터 차단 확인")
    class IssueCsrfToken_AttackVectorBlocked {

        @Test
        @DisplayName("엔드포인트는 guestId 파라미터가 아닌 검증된 토큰 클레임을 사용한다")
        void issueCsrfToken_noLongerAcceptsRawGuestId_requiresValidToken() {
            // given: 유효한 Bearer 토큰 — 클레임에서 guestId를 추출한다
            GuestClaims claims = new GuestClaims("guest-uuid", "nick", FUTURE_EXPIRY);
            when(guestTokenService.verify("valid-token")).thenReturn(claims);
            when(guestTokenService.issueCsrfToken(eq("guest-uuid"), anyLong())).thenReturn("csrf-token");

            // when: Bearer 토큰으로 호출 — 외부 guestId 파라미터 없음
            var response = guestAuthController.issueCsrfToken("Bearer valid-token");

            // then: 토큰이 검증되었고, 클레임의 guestId로 CSRF 토큰이 발급되었다
            assertThat(response.csrfToken()).isEqualTo("csrf-token");
            verify(guestTokenService).verify("valid-token"); // 토큰 검증이 반드시 수행되어야 한다
            verify(guestTokenService).issueCsrfToken(eq("guest-uuid"), anyLong()); // 클레임의 guestId 사용 확인
        }

        @Test
        @DisplayName("매우 긴 임의 문자열(Bearer 접두사 없음)은 처리 전에 UNAUTHORIZED로 차단된다")
        void issueCsrfToken_arbitraryLongString_withoutValidToken_throwsUnauthorized() {
            // given: 1000자의 임의 문자열 — Bearer 접두사 없음 (SEC-M1 원래 공격 패턴)
            String arbitraryLongInput = "not-a-bearer-token-" + "x".repeat(1000);

            // when & then: 토큰 검증이 전혀 수행되지 않고 UNAUTHORIZED 예외가 즉시 발생한다
            assertThatThrownBy(() ->
                    guestAuthController.issueCsrfToken(arbitraryLongInput)
            )
                    .isInstanceOf(GameException.class)
                    .satisfies(ex ->
                            assertThat(((GameException) ex).getErrorCode())
                                    .isEqualTo(ErrorCode.UNAUTHORIZED));

            // DoS 경로: guestTokenService.verify()가 호출되지 않아야 한다
            verify(guestTokenService, never()).verify(anyString());
        }

        @Test
        @DisplayName("빈 문자열 헤더는 UNAUTHORIZED 예외를 던진다")
        void issueCsrfToken_emptyString_throwsUnauthorized() {
            // when & then
            assertThatThrownBy(() ->
                    guestAuthController.issueCsrfToken("")
            )
                    .isInstanceOf(GameException.class)
                    .satisfies(ex ->
                            assertThat(((GameException) ex).getErrorCode())
                                    .isEqualTo(ErrorCode.UNAUTHORIZED));
        }
    }
}
