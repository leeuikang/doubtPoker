package org.doubt.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.doubt.auth.GuestClaims;
import org.doubt.auth.GuestTokenRequest;
import org.doubt.auth.GuestTokenResponse;
import org.doubt.auth.GuestTokenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class GuestAuthController {

    private final GuestTokenService guestTokenService;

    /**
     * Guest 토큰 발급 (H-2)
     * STOMP CONNECT 전 호출 — 발급된 토큰을 Authorization 헤더에 담아 연결
     */
    @PostMapping("/guest")
    public GuestTokenResponse issueGuestToken(@RequestBody @Valid GuestTokenRequest request) {
        String token = guestTokenService.issue(request.nickname());
        GuestClaims claims = guestTokenService.verify(token);
        String csrfToken = guestTokenService.issueCsrfToken(claims.guestId());
        return new GuestTokenResponse(token, claims.guestId(), claims.nickname(), csrfToken);
    }
}
