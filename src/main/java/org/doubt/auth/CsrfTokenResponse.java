package org.doubt.auth;

/**
 * CSRF 토큰 발급 응답 ({@code GET /auth/csrf}).
 *
 * <p>인증 토큰({@code GuestTokenResponse})과 채널을 분리하여 XSS 공격 시
 * 단일 HTTP 응답으로 두 토큰이 동시에 탈취되는 것을 방지한다. (R2-I3)</p>
 */
public record CsrfTokenResponse(String csrfToken) {}
