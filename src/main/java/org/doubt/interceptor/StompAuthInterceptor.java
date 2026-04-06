package org.doubt.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.auth.GuestClaims;
import org.doubt.auth.GuestTokenService;
import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * STOMP CONNECT 시 Guest 토큰을 검증하고 세션에 identity를 저장한다 (H-2)
 *
 * 클라이언트는 STOMP CONNECT 헤더에 아래 형태로 토큰을 전달해야 한다:
 *   Authorization: Bearer <token>
 *
 * 검증 성공 시 세션 속성에 저장:
 *   - "guestId"  : 서버 발급 식별자
 *   - "nickname" : 검증된 닉네임 (이후 message.sender() 대체)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GuestTokenService guestTokenService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) return message;

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("STOMP CONNECT rejected — Authorization header missing or malformed: sessionId={}", accessor.getSessionId());
            throw new GameException(ErrorCode.UNAUTHORIZED);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        GuestClaims claims = guestTokenService.verify(token); // 만료·서명 검증, 실패 시 GameException

        Map<String, Object> attrs = Objects.requireNonNull(accessor.getSessionAttributes());
        attrs.put("guestId", claims.guestId());
        attrs.put("nickname", claims.nickname());

        log.info("STOMP CONNECT authenticated: sessionId={}, nickname={}", accessor.getSessionId(), claims.nickname());
        return message;
    }
}
