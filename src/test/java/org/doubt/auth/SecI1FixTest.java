package org.doubt.auth;

import org.doubt.exception.GameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * SEC-I1 수정 검증 테스트
 *
 * <p>원래 문제(SEC-I1): {@code GuestTokenService.issueCsrfToken(String guestId)}가
 * 독립적인 만료 시각({@code now + expiryMillis})으로 CSRF 토큰을 발급해,
 * auth 토큰 만료 후에도 CSRF 토큰이 유효한 상태가 될 수 있었다.</p>
 *
 * <p>수정 후: {@code issueCsrfToken(String guestId, long expiresAt)}으로 서명 변경.
 * auth 토큰의 {@code expiresAt}을 그대로 전달해 두 토큰의 만료 시각을 일치시킨다.</p>
 *
 * <p>GuestClaims 레코드에 {@code expiresAt} 필드 추가 → {@code verify()} 결과에서
 * auth 만료 시각을 꺼낼 수 있다.</p>
 */
@DisplayName("SEC-I1 수정 검증: CSRF 토큰 만료를 auth 토큰에 연동")
class SecI1FixTest {

    private static final String VALID_SECRET = "a-valid-secret-key-that-is-exactly-32b!";
    private static final int EXPIRY_HOURS = 1;

    private final GuestTokenService service = new GuestTokenService(VALID_SECRET, EXPIRY_HOURS);

    // ----------------------------------------------------------------
    // GuestClaims.expiresAt 필드 존재 확인
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("GuestClaims 레코드에 expiresAt 필드가 있다")
    class GuestClaimsHasExpiresAt {

        @Test
        @DisplayName("verify() 결과로 반환된 GuestClaims에 expiresAt이 포함된다")
        void verify_returns_claims_with_expires_at() {
            String token = service.issue("Alice");
            GuestClaims claims = service.verify(token);

            assertThat(claims.expiresAt()).isGreaterThan(System.currentTimeMillis());
        }

        @Test
        @DisplayName("expiresAt은 발급 시각 + expiryHours * 3600_000 범위 안에 있다")
        void expires_at_is_within_expected_range() {
            long before = System.currentTimeMillis();
            String token = service.issue("Alice");
            long after = System.currentTimeMillis();

            GuestClaims claims = service.verify(token);

            long expectedMin = before + (long) EXPIRY_HOURS * 3600_000L;
            long expectedMax = after + (long) EXPIRY_HOURS * 3600_000L;
            assertThat(claims.expiresAt())
                    .isBetween(expectedMin, expectedMax);
        }
    }

    // ----------------------------------------------------------------
    // issueCsrfToken — auth expiresAt 연동
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("issueCsrfToken이 auth expiresAt에 연동된다")
    class CsrfExpiryLinkedToAuth {

        @Test
        @DisplayName("auth 만료 시각과 동일한 expiresAt으로 발급된 CSRF 토큰은 검증에 성공한다")
        void csrf_with_auth_expiry_verifies_successfully() {
            String authToken = service.issue("Alice");
            GuestClaims claims = service.verify(authToken);

            // auth expiresAt으로 CSRF 발급
            String csrfToken = service.issueCsrfToken(claims.guestId(), claims.expiresAt());

            assertThatNoException().isThrownBy(() -> service.verifyCsrfToken(csrfToken));
        }

        @Test
        @DisplayName("이미 만료된 expiresAt으로 발급된 CSRF 토큰은 즉시 TOKEN_EXPIRED 예외를 던진다")
        void csrf_with_past_expiry_throws_token_expired() {
            long pastExpiry = System.currentTimeMillis() - 1000; // 이미 만료

            String csrfToken = service.issueCsrfToken("any-guest-id", pastExpiry);

            assertThatThrownBy(() -> service.verifyCsrfToken(csrfToken))
                    .isInstanceOf(GameException.class);
        }

        @Test
        @DisplayName("auth 만료 시각이 미래이면 CSRF도 현재 유효하다")
        void csrf_expiry_matches_auth_expiry() {
            long futureExpiry = System.currentTimeMillis() + 7200_000L; // 2시간 후

            String csrfToken = service.issueCsrfToken("guest-xyz", futureExpiry);

            assertThatNoException().isThrownBy(() -> service.verifyCsrfToken(csrfToken));
        }
    }

    // ----------------------------------------------------------------
    // GuestClaims 레코드 구조 확인
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("GuestClaims 레코드 구조")
    class GuestClaimsStructure {

        @Test
        @DisplayName("GuestClaims는 guestId, nickname, expiresAt 세 필드를 갖는다")
        void guest_claims_has_three_components() {
            assertThat(GuestClaims.class.getRecordComponents()).hasSize(3);
        }

        @Test
        @DisplayName("세 번째 레코드 컴포넌트 이름이 expiresAt이다")
        void third_component_is_expires_at() {
            var components = GuestClaims.class.getRecordComponents();
            assertThat(components[2].getName()).isEqualTo("expiresAt");
        }
    }
}
