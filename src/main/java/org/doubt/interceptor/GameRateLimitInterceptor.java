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
 * 게임 액션 레이트 리밋 인터셉터 (SEC-M3)
 *
 * <p>/app/game/ 경로에만 적용. 세션별 분당 최대 {@value #MAX_ACTIONS_PER_WINDOW}건 제한.
 * 초당 수백 건 전송으로 인한 {@code synchronized(room)} 스레드 경합 및 상태 일관성 위협을 방지한다.</p>
 */
@Slf4j
@Component
public class GameRateLimitInterceptor implements ChannelInterceptor {

    static final int MAX_ACTIONS_PER_WINDOW = 60;
    private static final long WINDOW_MS = 60_000L;
    private static final String GAME_DEST_PREFIX = "/app/game/";

    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (!StompCommand.SEND.equals(accessor.getCommand())) return message;
        String dest = accessor.getDestination();
        if (dest == null || !dest.startsWith(GAME_DEST_PREFIX)) return message;

        String sessionId = accessor.getSessionId();
        if (sessionId == null) return message;

        RateLimitBucket bucket = buckets.computeIfAbsent(sessionId, id -> new RateLimitBucket());
        if (!bucket.tryAcquire()) {
            log.warn("GameRateLimit: sessionId={} exceeded {} actions/min on dest={} — message dropped",
                    sessionId, MAX_ACTIONS_PER_WINDOW, dest);
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
            return count.incrementAndGet() <= MAX_ACTIONS_PER_WINDOW;
        }
    }
}
