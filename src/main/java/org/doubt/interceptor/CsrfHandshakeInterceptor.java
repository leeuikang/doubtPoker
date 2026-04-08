package org.doubt.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.auth.GuestTokenService;
import org.doubt.constant.AppConstants;
import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 핸드셰이크 시 CSRF 토큰을 검증한다 (L-5)
 *
 * <p>클라이언트는 SockJS 연결 URL에 CSRF 토큰을 포함해야 한다:
 * <pre>new SockJS('/websocket?csrf=&lt;csrfToken&gt;')</pre>
 *
 * <p>검증 성공 시 {@code csrfGuestId}를 핸드셰이크 속성에 저장한다.
 * {@link StompAuthInterceptor}가 CONNECT 시 auth 토큰의 guestId와 교차 검증한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsrfHandshakeInterceptor implements HandshakeInterceptor {

    private final GuestTokenService guestTokenService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String csrfToken = extractQueryParam(request.getURI(), AppConstants.CSRF_PARAM);
        if (csrfToken == null || csrfToken.isBlank()) {
            log.warn("WebSocket handshake rejected — CSRF token missing: uri={}", request.getURI());
            throw new GameException(ErrorCode.UNAUTHORIZED);
        }

        String guestId = guestTokenService.verifyCsrfToken(csrfToken);
        attributes.put("csrfGuestId", guestId);

        log.debug("WebSocket handshake CSRF validated: guestId={}", guestId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private static String extractQueryParam(URI uri, String paramName) {
        String query = uri.getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = pair.substring(0, eq);
            if (paramName.equals(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }
}