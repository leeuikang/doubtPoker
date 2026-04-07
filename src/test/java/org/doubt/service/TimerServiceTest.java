package org.doubt.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("TimerService")
class TimerServiceTest {

    private TimerService timerService;

    @BeforeEach
    void setUp() {
        timerService = new TimerService();
    }

    @AfterEach
    void tearDown() {
        timerService.shutdown();
    }

    // ----------------------------------------------------------------
    // startThankYouTimer 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("startThankYouTimer")
    class StartThankYouTimer {

        @Test
        @DisplayName("지정된 시간이 지나면 콜백이 호출된다")
        void fires_callback_after_delay() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);

            timerService.startThankYouTimer("room1", 1, latch::countDown);

            boolean fired = latch.await(2, TimeUnit.SECONDS);
            assertThat(fired).isTrue();
        }

        @Test
        @DisplayName("cancelTimer 를 호출하면 만료 전 콜백이 실행되지 않는다")
        void cancel_before_expiry_prevents_callback() throws InterruptedException {
            AtomicBoolean called = new AtomicBoolean(false);

            timerService.startThankYouTimer("room2", 2, () -> called.set(true));
            timerService.cancelTimer("room2");

            Thread.sleep(300);
            assertThat(called.get()).isFalse();
        }

        @Test
        @DisplayName("두 번째 타이머 시작 시 첫 번째 타이머가 취소되어 첫 번째 콜백이 호출되지 않는다")
        void second_timer_cancels_first_timer() throws InterruptedException {
            AtomicBoolean firstCalled = new AtomicBoolean(false);
            CountDownLatch secondLatch = new CountDownLatch(1);

            timerService.startThankYouTimer("room3", 2, () -> firstCalled.set(true));
            timerService.startThankYouTimer("room3", 1, secondLatch::countDown);

            boolean secondFired = secondLatch.await(2, TimeUnit.SECONDS);
            assertThat(secondFired).isTrue();

            Thread.sleep(200);
            assertThat(firstCalled.get()).isFalse();
        }
    }

    // ----------------------------------------------------------------
    // cancelTimer 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("cancelTimer")
    class CancelTimer {

        @Test
        @DisplayName("존재하지 않는 roomId 에 cancelTimer 를 호출해도 예외가 발생하지 않는다")
        void cancel_unknown_room_does_not_throw() {
            assertThatCode(() -> timerService.cancelTimer("nonexistent-room"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이미 완료된 타이머에 cancelTimer 를 호출해도 예외가 발생하지 않는다")
        void cancel_already_completed_timer_does_not_throw() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);

            timerService.startThankYouTimer("room4", 1, latch::countDown);
            latch.await(2, TimeUnit.SECONDS);

            assertThatCode(() -> timerService.cancelTimer("room4"))
                    .doesNotThrowAnyException();
        }
    }

    // ----------------------------------------------------------------
    // startTurnTimer 취소 동작 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("startTurnTimer")
    class StartTurnTimer {

        @Test
        @DisplayName("startTurnTimer 직후 cancelTimer 하면 콜백이 호출되지 않는다")
        void cancel_after_start_prevents_callback() throws InterruptedException {
            AtomicBoolean called = new AtomicBoolean(false);

            timerService.startTurnTimer("room5", "p1", () -> called.set(true));
            timerService.cancelTimer("room5");

            Thread.sleep(300);
            assertThat(called.get()).isFalse();
        }

        @Test
        @DisplayName("빈 문자열 roomId 로 startTurnTimer 를 호출하고 cancelTimer 로 취소할 수 있다")
        void empty_room_id_can_be_started_and_cancelled() throws InterruptedException {
            AtomicBoolean called = new AtomicBoolean(false);

            timerService.startTurnTimer("", "p1", () -> called.set(true));
            timerService.cancelTimer("");

            Thread.sleep(300);
            assertThat(called.get()).isFalse();
        }

        @Test
        @DisplayName("두 번째 startTurnTimer 가 첫 번째 타이머를 취소한다 (첫 번째 콜백 미호출)")
        void second_turn_timer_cancels_first() throws InterruptedException {
            AtomicBoolean firstCalled = new AtomicBoolean(false);

            timerService.startTurnTimer("room6", "p1", () -> firstCalled.set(true));
            timerService.startTurnTimer("room6", "p1", () -> {});
            timerService.cancelTimer("room6");

            Thread.sleep(300);
            assertThat(firstCalled.get()).isFalse();
        }

        @Test
        @DisplayName("서로 다른 roomId 는 독립적으로 관리된다 (room7 취소가 room8 에 영향 없음)")
        void different_room_ids_are_independent() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);

            timerService.startTurnTimer("room7", "p1", () -> {});
            timerService.startThankYouTimer("room8", 1, latch::countDown);
            timerService.cancelTimer("room7");

            boolean fired = latch.await(2, TimeUnit.SECONDS);
            assertThat(fired).isTrue();
        }
    }
}
