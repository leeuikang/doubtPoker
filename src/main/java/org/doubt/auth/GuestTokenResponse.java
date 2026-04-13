package org.doubt.auth;

/**
 * 게스트 인증 토큰 발급 응답.
 *
 * <p>CSRF 토큰은 보안상 별도 엔드포인트({@code GET /auth/csrf})를 통해 발급한다.
 * 두 토큰을 동일 HTTP 응답에 포함하면 XSS 공격 시 단일 호출로 동시 탈취가 가능하므로 분리한다. (R2-I3)</p>
 */
public record GuestTokenResponse(String token, String guestId, String nickname) {}
