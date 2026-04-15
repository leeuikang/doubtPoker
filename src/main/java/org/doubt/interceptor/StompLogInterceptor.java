package org.doubt.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class StompLogInterceptor implements ChannelInterceptor {

    // SEC-M2: 민감 정보가 포함될 수 있는 destination prefix — payload를 DEBUG 레벨로 격하
    // /app/game/ : 손패·멜드 등 게임 상태 정보
    // /app/chat/ : 채팅 내용 (개인정보 + 로그 인젝션 경로)
    private static final String SENSITIVE_APP_PREFIX = "/app/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            String dest = accessor.getDestination();
            Object raw = message.getPayload();
            String payload = (raw instanceof byte[] bytes) ? new String(bytes, StandardCharsets.UTF_8) : raw.toString();

            // /app/ 하위 모든 경로(게임 액션·채팅)는 payload에 민감 정보 포함 가능 — DEBUG로 격하 (SEC-M2)
            if (dest != null && dest.startsWith(SENSITIVE_APP_PREFIX)) {
                log.debug("InBound: sessionId={}, dest={}, payload={}", accessor.getSessionId(), dest, payload);
            } else {
                log.info("InBound: sessionId={}, dest={}", accessor.getSessionId(), dest);
            }
        }

        return message;
    }

}
