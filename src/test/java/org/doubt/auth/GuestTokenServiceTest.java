package org.doubt.auth;

import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GuestTokenService")
class GuestTokenServiceTest {

    // SEC-L2: 시크릿 키는 최소 32바이트 이상이어야 한다
    private static final String SECRET = "test-secret-key-that-is-32bytes!";
    private static final int EXPIRY_HOURS = 1;

    private GuestTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new GuestTokenService(SECRET, EXPIRY_HOURS);
    }

    // ----------------------------------------------------------------
    // 테스트용 헬퍼: 동일한 비밀키 + 서비스의 instanceId로 HMAC 서명 생성
    // ----------------------------------------------------------------
    private String buildValidTokenWithExpiry(String nickname, long expiresAt) throws Exception {
        String guestId = "test-guest-id";
        long issuedAt = System.currentTimeMillis();
        // instanceId는 서비스에서 가져와야 재시작 무효화 검증을 통과할 수 있다
        String raw = guestId + ":" + issuedAt + ":" + expiresAt + ":" + tokenService.getInstanceId() + ":" + nickname;

        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sigBytes = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
        String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

        return payloadB64 + "." + signatureB64;
    }

    // ----------------------------------------------------------------

    @Nested
    @DisplayName("issue")
    class Issue {

        @Test
        @DisplayName("발급된 토큰은 즉시 검증에 성공하고 올바른 닉네임과 null이 아닌 guestId를 반환한다")
        void issued_token_verifies_successfully() {
            String nickname = "홍길동";
            String token = tokenService.issue(nickname);

            GuestClaims claims = tokenService.verify(token);

            assertThat(claims.nickname()).isEqualTo(nickname);
            assertThat(claims.guestId()).isNotNull();
        }

        @Test
        @DisplayName("서로 다른 닉네임으로 발급하면 서로 다른 토큰이 생성된다")
        void different_nicknames_produce_different_tokens() {
            String token1 = tokenService.issue("alice");
            String token2 = tokenService.issue("bob");

            assertThat(token1).isNotEqualTo(token2);
        }

        @Test
        @DisplayName("같은 닉네임으로 두 번 발급해도 UUID가 달라 서로 다른 토큰이 생성된다")
        void same_nickname_produces_different_tokens_each_time() {
            String token1 = tokenService.issue("alice");
            String token2 = tokenService.issue("alice");

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    // ----------------------------------------------------------------
    // 테스트용 헬퍼: 만료 시각이 지정된 CSRF 토큰 빌드
    // ----------------------------------------------------------------
    private String buildValidCsrfTokenWithExpiry(String guestId, long expiresAt) throws Exception {
        long now = System.currentTimeMillis();
        String raw = guestId + ":" + now + ":" + expiresAt;
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sigBytes = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
        String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        return payloadB64 + "." + signatureB64;
    }

    // ----------------------------------------------------------------

    @Nested
    @DisplayName("issueCsrfToken / verifyCsrfToken")
    class CsrfToken {

        @Test
        @DisplayName("발급한 CSRF 토큰은 즉시 검증에 성공하고 올바른 guestId를 반환한다")
        void issued_csrf_token_verifies_successfully() {
            String guestId = "some-guest-uuid";
            long expiresAt = System.currentTimeMillis() + 3600_000L;
            String csrfToken = tokenService.issueCsrfToken(guestId, expiresAt);

            assertThatNoException().isThrownBy(() -> {
                String returnedGuestId = tokenService.verifyCsrfToken(csrfToken);
                assertThat(returnedGuestId).isEqualTo(guestId);
            });
        }

        @Test
        @DisplayName("만료된 CSRF 토큰은 TOKEN_EXPIRED 예외를 던진다")
        void expired_csrf_token_throws_token_expired() throws Exception {
            long pastExpiry = System.currentTimeMillis() - 1000;
            String expiredCsrfToken = buildValidCsrfTokenWithExpiry("guest-id", pastExpiry);

            assertThatThrownBy(() -> tokenService.verifyCsrfToken(expiredCsrfToken))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("서명이 변조된 CSRF 토큰은 TOKEN_INVALID 예외를 던진다")
        void tampered_csrf_token_throws_token_invalid() {
            String csrfToken = tokenService.issueCsrfToken("guest-id", System.currentTimeMillis() + 3600_000L);
            String[] parts = csrfToken.split("\\.", 2);
            String tampered = parts[0] + ".INVALIDSIGNATURE";

            assertThatThrownBy(() -> tokenService.verifyCsrfToken(tampered))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_INVALID));
        }

        @Test
        @DisplayName("점(.) 구분자가 없는 CSRF 토큰은 TOKEN_INVALID 예외를 던진다")
        void malformed_csrf_token_throws_token_invalid() {
            assertThatThrownBy(() -> tokenService.verifyCsrfToken("nodottoken"))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_INVALID));
        }

        @Test
        @DisplayName("null CSRF 토큰은 TOKEN_INVALID 예외를 던진다")
        void null_csrf_token_throws_token_invalid() {
            assertThatThrownBy(() -> tokenService.verifyCsrfToken(null))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_INVALID));
        }
    }

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("서명을 임의로 변조하면 TOKEN_INVALID 예외가 발생한다")
        void tampered_signature_throws_token_invalid() {
            String token = tokenService.issue("player");
            String[] parts = token.split("\\.", 2);
            String tamperedToken = parts[0] + ".INVALIDSIGNATURE";

            assertThatThrownBy(() -> tokenService.verify(tamperedToken))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_INVALID));
        }

        @Test
        @DisplayName("페이로드를 임의로 변조하면 TOKEN_INVALID 예외가 발생한다")
        void tampered_payload_throws_token_invalid() {
            String token = tokenService.issue("player");
            String[] parts = token.split("\\.", 2);

            // 다른 닉네임으로 페이로드를 만들어 원본 서명과 조합
            String fakeRaw = "fake-guest-id:0:99999999999999:fake-instance:attacker";
            String fakePayloadB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(fakeRaw.getBytes(StandardCharsets.UTF_8));
            String tamperedToken = fakePayloadB64 + "." + parts[1];

            assertThatThrownBy(() -> tokenService.verify(tamperedToken))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_INVALID));
        }

        @Test
        @DisplayName("만료 시각이 과거인 토큰은 TOKEN_EXPIRED 예외가 발생한다")
        void expired_token_throws_token_expired() throws Exception {
            long pastExpiry = System.currentTimeMillis() - 1000; // 1초 전에 만료
            String expiredToken = buildValidTokenWithExpiry("player", pastExpiry);

            assertThatThrownBy(() -> tokenService.verify(expiredToken))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_EXPIRED));
        }

        @Test
        @DisplayName("점(.) 구분자가 없는 토큰은 TOKEN_INVALID 예외가 발생한다")
        void malformed_token_without_dot_throws_token_invalid() {
            assertThatThrownBy(() -> tokenService.verify("nodottoken"))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_INVALID));
        }

        @Test
        @DisplayName("페이로드 필드 수가 부족한 토큰은 TOKEN_INVALID 예외가 발생한다")
        void token_with_wrong_payload_fields_throws_token_invalid() {
            // 필드가 4개뿐인 페이로드(nickname 누락)로 올바른 서명을 붙여 검증
            try {
                String raw = "guest-id:12345:99999999999999:some-instance-id"; // 5번째 필드(nickname) 없음
                String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] sigBytes = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
                String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
                String token = payloadB64 + "." + signatureB64;

                assertThatThrownBy(() -> tokenService.verify(token))
                        .isInstanceOf(GameException.class)
                        .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.TOKEN_INVALID));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("만료 시각 필드가 숫자가 아닌 토큰은 TOKEN_INVALID 예외가 발생한다")
        void token_with_non_numeric_expiry_throws_token_invalid() {
            try {
                String raw = "guest-id:12345:not-a-number:some-instance-id:nickname";
                String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] sigBytes = mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8));
                String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
                String token = payloadB64 + "." + signatureB64;

                assertThatThrownBy(() -> tokenService.verify(token))
                        .isInstanceOf(GameException.class)
                        .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                                .isEqualTo(ErrorCode.TOKEN_INVALID));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("다른 서버 인스턴스가 발급한 토큰은 TOKEN_INVALID 예외가 발생한다 (재시작 무효화)")
        void token_from_different_instance_throws_token_invalid() throws Exception {
            // 다른 instanceId로 별도 서비스 인스턴스를 생성하여 토큰 발급
            GuestTokenService otherInstance = new GuestTokenService(SECRET, EXPIRY_HOURS);
            String tokenFromOtherInstance = otherInstance.issue("player");

            // 현재 tokenService로 검증 → instanceId 불일치 → TOKEN_INVALID
            assertThatThrownBy(() -> tokenService.verify(tokenFromOtherInstance))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_INVALID));
        }

        @Test
        @DisplayName("콜론이 포함된 닉네임도 올바르게 파싱된다")
        void nickname_containing_colon_is_parsed_correctly() {
            String nickname = "user:name:with:colons";
            String token = tokenService.issue(nickname);

            GuestClaims claims = tokenService.verify(token);

            assertThat(claims.nickname()).isEqualTo(nickname);
        }
    }
}
