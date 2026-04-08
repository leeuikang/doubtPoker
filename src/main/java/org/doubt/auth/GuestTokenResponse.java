package org.doubt.auth;

public record GuestTokenResponse(String token, String guestId, String nickname, String csrfToken) {}
