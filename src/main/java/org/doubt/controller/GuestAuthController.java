package org.doubt.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.doubt.auth.CsrfTokenResponse;
import org.doubt.auth.GuestClaims;
import org.doubt.auth.GuestTokenRequest;
import org.doubt.auth.GuestTokenResponse;
import org.doubt.auth.GuestTokenService;
import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class GuestAuthController {

    private final GuestTokenService guestTokenService;

    /**
     * Guest 인증 토큰 발급 (H-2)
     *
     * <p>STOMP CONNECT 전 호출 — 발급된 토큰을 Authorization 헤더에 담아 연결한다.
     * CSRF 토큰은 별도로 {@code GET /auth/csrf?guestId=...}를 통해 발급받는다. (R2-I3)</p>
     */
    @PostMapping("/guest")
    public GuestTokenResponse issueGuestToken(@RequestBody @Valid GuestTokenRequest request) {
        String token = guestTokenService.issue(request.nickname());
        GuestClaims claims = guestTokenService.verify(token);
        return new GuestTokenResponse(token, claims.guestId(), claims.nickname());
    }

    /**
     * CSRF 토큰 발급 — WebSocket 핸드셰이크 전 호출 (R2-I3)
     *
     * <p>인증 토큰({@code POST /auth/guest})과 채널을 분리하여 XSS 공격 시
     * 단일 호출로 두 토큰이 동시에 탈취되는 것을 방지한다.</p>
     *
     * @param guestId {@code POST /auth/guest} 응답에서 수신한 guestId
     */
    @GetMapping("/csrf")
    public CsrfTokenResponse issueCsrfToken(@RequestParam String guestId) {
        if (guestId.isBlank()) {
            throw new GameException(ErrorCode.UNAUTHORIZED);
        }
        String csrfToken = guestTokenService.issueCsrfToken(guestId);
        return new CsrfTokenResponse(csrfToken);
    }
}
