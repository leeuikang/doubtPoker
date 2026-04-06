package org.doubt.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 채팅 메시지 레이트 리밋 인터셉터 (L-3)
 * - 세션별 분당 최대 {@value #MAX_MESSAGES_PER_WINDOW} 건 제한
 * - /app/chat/message destination에만 적용
 */
@Slf4j
@Component
public class ChatRateLimitInterceptor implements ChannelInterceptor {

    static final int MAX_MESSAGES_PER_WINDOW = 20;
    private static final long WINDOW_MS = 60_000L;
    private static final String CHAT_DEST = "/app/chat/message";

    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.SEND.equals(accessor.getCommand())) return message;
        if (!CHAT_DEST.equals(accessor.getDestination())) return message;

        String sessionId = accessor.getSessionId();
        if (sessionId == null) return message;

        RateLimitBucket bucket = buckets.computeIfAbsent(sessionId, id -> new RateLimitBucket());
        if (!bucket.tryAcquire()) {
            log.warn("RateLimit: sessionId={} exceeded {} messages/min — message dropped", sessionId, MAX_MESSAGES_PER_WINDOW);
            return null;
        }
        return message;
    }

    /** DISCONNECT 시 버킷 정리 */
    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            buckets.remove(accessor.getSessionId());
        }
    }

    private static class RateLimitBucket {
        private long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= WINDOW_MS) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= MAX_MESSAGES_PER_WINDOW;
        }
    }
}
