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
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

/**
 * Guest 토큰 발급 및 검증 서비스 (H-2)
 *
 * 토큰 포맷: {Base64Url(payload)}.{Base64Url(HMAC-SHA256)}
 * payload  : {guestId}:{issuedAt}:{expiresAt}:{nickname}
 *   - guestId   : UUID
 *   - issuedAt  : Unix 밀리초
 *   - expiresAt : Unix 밀리초
 *   - nickname  : 마지막 필드 (콜론 포함 가능하도록 split 4번째 이후로 처리)
 */
@Slf4j
@Service
public class GuestTokenService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int PAYLOAD_FIELDS = 4;

    private final byte[] secretKeyBytes;
    private final long expiryMillis;

    public GuestTokenService(
            @Value("${app.auth.secret}") String secret,
            @Value("${app.auth.expiry-hours}") int expiryHours) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expiryMillis = (long) expiryHours * 3600_000L;
    }

    public String issue(String nickname) {
        String guestId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long expiresAt = now + expiryMillis;

        String raw = guestId + ":" + now + ":" + expiresAt + ":" + nickname;
        String payloadB64 = base64Encode(raw);
        String signatureB64 = base64Encode(hmac(payloadB64));
        return payloadB64 + "." + signatureB64;
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

        if (System.currentTimeMillis() > expiresAt) {
            throw new GameException(ErrorCode.TOKEN_EXPIRED);
        }

        return new GuestClaims(fields[0], fields[3]);
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

    /** 타이밍 공격 방지를 위한 상수 시간 비교 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
