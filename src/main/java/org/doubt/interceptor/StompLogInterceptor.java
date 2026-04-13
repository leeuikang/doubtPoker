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

    // 게임 액션 destination prefix — 손패 등 민감 정보가 payload에 포함될 수 있음 (R2-W8)
    private static final String SENSITIVE_DEST_PREFIX = "/app/game/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            String dest = accessor.getDestination();
            Object raw = message.getPayload();
            String payload = (raw instanceof byte[] bytes) ? new String(bytes, StandardCharsets.UTF_8) : raw.toString();

            // 게임 관련 destination 전체 — 손패 정보 노출 방지를 위해 DEBUG 레벨로 격하 (R2-W8)
            if (dest != null && dest.startsWith(SENSITIVE_DEST_PREFIX)) {
                log.debug("InBound: sessionId={}, dest={}, payload={}", accessor.getSessionId(), dest, payload);
            } else {
                log.info("InBound: sessionId={}, dest={}, payload={}", accessor.getSessionId(), dest, payload);
            }
        }

        return message;
    }

}
