package org.doubt.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * SEC-L1, SEC-L2 보안 수정 검증 테스트
 *
 * <p>SEC-L1: {@code constantTimeEquals} 를 {@code MessageDigest.isEqual}로 교체.
 * JDK 표준 타이밍 안전성 보장 — 길이 불일치 시에도 상수 시간 보장.</p>
 *
 * <p>SEC-L2: 생성자에서 시크릿 키 최소 32바이트 검증.
 * 짧은 키 사용 시 {@code IllegalArgumentException} 발생.</p>
 */
@DisplayName("SEC-L1, SEC-L2 수정 검증")
class SecL1L2FixTest {

    private static final String VALID_SECRET = "a-valid-secret-key-that-is-exactly-32b!";
    private static final int EXPIRY_HOURS = 1;

    // ----------------------------------------------------------------
    // SEC-L2: 시크릿 키 최소 길이 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("SEC-L2: 생성자 시크릿 키 길이 검증")
    class SecL2KeyLength {

        @Test
        @DisplayName("32바이트 미만 키로 생성 시 IllegalArgumentException이 발생한다")
        void short_key_throws_illegal_argument() {
            String shortSecret = "short"; // 5바이트

            assertThatThrownBy(() -> new GuestTokenService(shortSecret, EXPIRY_HOURS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32 bytes");
        }

        @Test
        @DisplayName("31바이트 키도 IllegalArgumentException이 발생한다")
        void exactly_31_byte_key_throws() {
            String secret31 = "a".repeat(31); // 31바이트

            assertThatThrownBy(() -> new GuestTokenService(secret31, EXPIRY_HOURS))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("정확히 32바이트 키는 정상 생성된다")
        void exactly_32_byte_key_succeeds() {
            String secret32 = "a".repeat(32); // 32바이트

            assertThatNoException().isThrownBy(() -> new GuestTokenService(secret32, EXPIRY_HOURS));
        }

        @Test
        @DisplayName("32바이트 이상 키는 정상 생성된다")
        void longer_key_succeeds() {
            assertThatNoException().isThrownBy(() -> new GuestTokenService(VALID_SECRET, EXPIRY_HOURS));
        }
    }

    // ----------------------------------------------------------------
    // SEC-L1: constantTimeEquals → MessageDigest.isEqual
    // 직접적인 내부 구현 검증보다 토큰 검증 동작을 통해 간접 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("SEC-L1: 서명 검증 동작 — constantTimeEquals 교체 후에도 정확히 동작")
    class SecL1ConstantTimeEquals {

        private final GuestTokenService service = new GuestTokenService(VALID_SECRET, EXPIRY_HOURS);

        @Test
        @DisplayName("유효한 토큰 서명은 검증에 통과한다")
        void valid_signature_passes_verification() {
            String token = service.issue("Alice");
            assertThatNoException().isThrownBy(() -> service.verify(token));
        }

        @Test
        @DisplayName("서명 1글자 변경 시 TOKEN_INVALID 예외가 발생한다")
        void tampered_signature_one_char_fails() {
            String token = service.issue("Alice");
            // 마지막 문자를 변경해 서명 조작
            String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

            assertThatThrownBy(() -> service.verify(tampered))
                    .isInstanceOf(org.doubt.exception.GameException.class);
        }

        @Test
        @DisplayName("서명 길이가 다른 토큰도 TOKEN_INVALID 예외가 발생한다 — 상수시간 보장")
        void different_length_signature_fails() {
            String token = service.issue("Alice");
            // 서명 부분을 더 짧게 잘라냄
            int dotIdx = token.lastIndexOf('.');
            String truncated = token.substring(0, dotIdx + 1) + token.substring(dotIdx + 1, token.length() - 3);

            assertThatThrownBy(() -> service.verify(truncated))
                    .isInstanceOf(org.doubt.exception.GameException.class);
        }

        @Test
        @DisplayName("CSRF 토큰도 서명 조작 시 TOKEN_INVALID 예외가 발생한다")
        void tampered_csrf_signature_fails() {
            String authToken = service.issue("Bob");
            GuestClaims claims = service.verify(authToken);
            String csrfToken = service.issueCsrfToken(claims.guestId(), claims.expiresAt());

            String tampered = csrfToken.substring(0, csrfToken.length() - 1) + (csrfToken.endsWith("A") ? "B" : "A");

            assertThatThrownBy(() -> service.verifyCsrfToken(tampered))
                    .isInstanceOf(org.doubt.exception.GameException.class);
        }
    }
}
