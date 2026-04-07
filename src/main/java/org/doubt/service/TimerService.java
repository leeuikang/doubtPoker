package org.doubt.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.GameConstants;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 타이머 관리 서비스
 *
 * <p>방별로 최대 1개의 활성 타이머를 관리한다.
 * 새 타이머 시작 전에 기존 타이머를 자동 취소한다.</p>
 *
 * <p>콜백(Runnable)은 호출자(GameController)가 제공하므로
 * RoundService 와의 순환 의존이 없다.</p>
 *
 * 타이머 종류:
 *   - 턴 타이머     : {@value GameConstants#TURN_TIMER_SEC}초 (초과 시 자동 드로우+최고카드 버림)
 *   - 땡큐 타이머   : 게임 시작 시 {@value GameConstants#THANK_YOU_TIMER_START_SEC}초,
 *                     버린 직후 {@value GameConstants#THANK_YOU_TIMER_DISCARD_SEC}초
 */
@Slf4j
@Service
public class TimerService {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4);

    /** roomId → 활성 타이머 Future */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> activeFutures =
            new ConcurrentHashMap<>();

    /**
     * 플레이어 턴 타이머 시작 ({@value GameConstants#TURN_TIMER_SEC}초).
     * 기존 타이머가 있으면 먼저 취소한다.
     *
     * @param onTimeout 타이머 만료 시 실행할 콜백 (GameController 에서 제공)
     */
    public void startTurnTimer(String roomId, String playerId, Runnable onTimeout) {
        cancelTimer(roomId);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            log.info("[Timer] turn timeout roomId={} playerId={}", roomId, playerId);
            try {
                onTimeout.run();
            } catch (Exception e) {
                log.error("[Timer] error in turn timeout callback roomId={}", roomId, e);
            }
        }, GameConstants.TURN_TIMER_SEC, TimeUnit.SECONDS);
        activeFutures.put(roomId, future);
        log.info("[Timer] turn timer started roomId={} playerId={}", roomId, playerId);
    }

    /**
     * 땡큐 타이머 시작.
     * 기존 타이머가 있으면 먼저 취소한다.
     *
     * @param seconds  {@value GameConstants#THANK_YOU_TIMER_START_SEC} (게임 시작) 또는
     *                 {@value GameConstants#THANK_YOU_TIMER_DISCARD_SEC} (버린 직후)
     * @param onExpiry 타이머 만료(땡큐 선언 없음) 시 실행할 콜백
     */
    public void startThankYouTimer(String roomId, int seconds, Runnable onExpiry) {
        cancelTimer(roomId);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            log.info("[Timer] thank-you timer expired roomId={} sec={}", roomId, seconds);
            try {
                onExpiry.run();
            } catch (Exception e) {
                log.error("[Timer] error in thank-you callback roomId={}", roomId, e);
            }
        }, seconds, TimeUnit.SECONDS);
        activeFutures.put(roomId, future);
        log.info("[Timer] thank-you timer started roomId={} sec={}", roomId, seconds);
    }

    /**
     * 해당 방의 활성 타이머를 취소한다.
     * 이미 완료됐거나 없으면 무시한다.
     */
    public void cancelTimer(String roomId) {
        ScheduledFuture<?> existing = activeFutures.remove(roomId);
        if (existing != null && !existing.isDone()) {
            existing.cancel(false);
            log.info("[Timer] cancelled roomId={}", roomId);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        log.info("[Timer] scheduler shut down");
    }
}
