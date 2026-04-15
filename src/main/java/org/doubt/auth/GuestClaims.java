package org.doubt.auth;

/** auth 토큰 검증 결과 (SEC-I1: expiresAt을 포함해 CSRF 토큰 만료를 연동한다) */
public record GuestClaims(String guestId, String nickname, long expiresAt) {}
