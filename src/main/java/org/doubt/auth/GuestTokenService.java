package org.doubt.auth;

import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Guest 토큰 발급 및 검증 서비스 (H-2)
 *
 * 토큰 포맷: {Base64Url(payload)}.{Base64Url(HMAC-SHA256)}
 * payload  : {guestId}:{issuedAt}:{expiresAt}:{instanceId}:{nickname}
 *   - guestId    : UUID (발급마다 고유)
 *   - issuedAt   : Unix 밀리초
 *   - expiresAt  : Unix 밀리초
 *   - instanceId : 서버 기동 시 생성된 UUID — 재시작 시 기존 토큰 전체 무효화
 *   - nickname   : 마지막 필드 (콜론 포함 가능하도록 split(5) 이후로 처리)
 */
@Slf4j
@Service
public class GuestTokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int PAYLOAD_FIELDS = 5;
    private static final int CSRF_PAYLOAD_FIELDS = 3;

    private final byte[] secretKeyBytes;
    private final long expiryMillis;
    private final String instanceId;

    public GuestTokenService(
            @Value("${app.auth.secret}") String secret,
            @Value("${app.auth.expiry-hours}") int expiryHours) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // SEC-L2: 시크릿 키 최소 32바이트 검증 — HMAC-SHA256 보안 강도 보장
        if (this.secretKeyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "Secret key must be at least 32 bytes for HMAC-SHA256, got " + this.secretKeyBytes.length);
        }
        this.expiryMillis = (long) expiryHours * 3600_000L;
        this.instanceId = UUID.randomUUID().toString();
        log.info("GuestTokenService initialized: instanceId={}", instanceId);
    }

    public String issue(String nickname) {
        String guestId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long expiresAt = now + expiryMillis;

        String raw = guestId + ":" + now + ":" + expiresAt + ":" + instanceId + ":" + nickname;
        String payloadB64 = base64Encode(raw);
        String signatureB64 = base64Encode(hmac(payloadB64));
        return payloadB64 + "." + signatureB64;
    }

    /**
     * CSRF 토큰 발급 (포맷: {Base64Url(guestId:issuedAt:expiresAt)}.{HMAC(payload)})
     * 무상태(stateless) — auth 토큰과 동일한 만료 시간 적용.
     */
    public String issueCsrfToken(String guestId) {
        long now = System.currentTimeMillis();
        long expiresAt = now + expiryMillis;
        String raw = guestId + ":" + now + ":" + expiresAt;
        String payloadB64 = base64Encode(raw);
        String sig = base64Encode(hmac(payloadB64));
        return payloadB64 + "." + sig;
    }

    /**
     * CSRF 토큰 검증 후 guestId 반환.
     * 서명 불일치 시 TOKEN_INVALID, 만료 시 TOKEN_EXPIRED 예외.
     */
    public String verifyCsrfToken(String csrfToken) {
        if (csrfToken == null) throw new GameException(ErrorCode.TOKEN_INVALID);
        String[] parts = csrfToken.split("\\.", 2);
        if (parts.length != 2) throw new GameException(ErrorCode.TOKEN_INVALID);

        String payloadB64 = parts[0];
        String expected = base64Encode(hmac(payloadB64));
        if (!constantTimeEquals(expected, parts[1])) throw new GameException(ErrorCode.TOKEN_INVALID);

        String raw = base64Decode(payloadB64);
        String[] fields = raw.split(":", CSRF_PAYLOAD_FIELDS);
        if (fields.length != CSRF_PAYLOAD_FIELDS) throw new GameException(ErrorCode.TOKEN_INVALID);

        long expiresAt;
        try {
            expiresAt = Long.parseLong(fields[2]);
        } catch (NumberFormatException e) {
            throw new GameException(ErrorCode.TOKEN_INVALID);
        }

        if (System.currentTimeMillis() > expiresAt) throw new GameException(ErrorCode.TOKEN_EXPIRED);

        return fields[0]; // guestId
    }

    public GuestClaims verify(String token) {
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) throw new GameException(ErrorCode.TOKEN_INVALID);

        String payloadB64 = parts[0];
        String signatureB64 = parts[1];

        // 서명 검증
        String expectedSig = base64Encode(hmac(payloadB64));
        if (!constantTimeEquals(expectedSig, signatureB64)) {
            throw new GameException(ErrorCode.TOKEN_INVALID);
        }

        // 페이로드 파싱
        String raw = base64Decode(payloadB64);
        String[] fields = raw.split(":", PAYLOAD_FIELDS);
        if (fields.length != PAYLOAD_FIELDS) throw new GameException(ErrorCode.TOKEN_INVALID);

        long expiresAt;
        try {
            expiresAt = Long.parseLong(fields[2]);
        } catch (NumberFormatException e) {
            throw new GameException(ErrorCode.TOKEN_INVALID);
        }

        // 서버 인스턴스 검증 — 재시작 후에는 기존 토큰 무효화
        if (!instanceId.equals(fields[3])) {
            throw new GameException(ErrorCode.TOKEN_INVALID);
        }

        if (System.currentTimeMillis() > expiresAt) {
            throw new GameException(ErrorCode.TOKEN_EXPIRED);
        }

        return new GuestClaims(fields[0], fields[4]);
    }

    /** 테스트에서 instanceId 주입 토큰 생성 시 사용 */
    String getInstanceId() {
        return instanceId;
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new GameException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    private static String base64Encode(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Encode(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String base64Decode(String s) {
        return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
    }

    /**
     * 타이밍 공격 방지를 위한 상수 시간 비교 (SEC-L1)
     * JDK 표준 {@link MessageDigest#isEqual}은 길이 불일치 시에도 상수 시간을 보장한다.
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
