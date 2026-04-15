package org.doubt.service;

import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * GL-W4 코드 품질 수정 검증 테스트
 *
 * <p>원래 문제(GL-W4): {@code ScoreService}에 {@code @Slf4j}가 없어
 * 로그를 남길 수 없었다.</p>
 *
 * <p>수정 후: {@code @Slf4j} 추가 및 훌라 판정 시 INFO 로그 기록.</p>
 */
@DisplayName("GL-W4 수정 검증: ScoreService @Slf4j 추가")
class GlW4FixTest {

    private final ScoreService scoreService = new ScoreService();

    // ----------------------------------------------------------------
    // @Slf4j 선언 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("@Slf4j 필드 존재 검증")
    class Slf4jField {

        @Test
        @DisplayName("ScoreService에 log 필드가 존재한다")
        void score_service_has_log_field() throws NoSuchFieldException {
            Field logField = ScoreService.class.getDeclaredField("log");
            logField.setAccessible(true);
            assertThat(logField.getType()).isAssignableTo(Logger.class);
        }
    }

    // ----------------------------------------------------------------
    // 훌라 판정 로그 간접 검증 — 정상 동작 확인
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("훌라 판정 시 로그가 기록되어도 결과가 올바르다")
    class HulaLogging {

        @Test
        @DisplayName("훌라 시 scoreService가 정상 동작한다 (log 호출 중 예외 없음)")
        void hula_calculation_does_not_throw() {
            // Alice: 멜드 없이 고잉아웃 (훌라)
            PlayerRoundState aliceState = new PlayerRoundState();
            aliceState.setPlayerId("Alice");
            aliceState.setHand(List.of()); // 빈 손패
            aliceState.setHadMeldsAtTurnStart(false); // 훌라 조건

            // Bob: 패배자
            PlayerRoundState bobState = new PlayerRoundState();
            bobState.setPlayerId("Bob");
            bobState.setHand(List.of(new Card(Suit.SPADE, Rank.FIVE)));
            bobState.setHadMeldsAtTurnStart(false);

            Map<String, PlayerRoundState> playerStates = new HashMap<>();
            playerStates.put("Alice", aliceState);
            playerStates.put("Bob", bobState);

            assertThatCode(() ->
                    scoreService.calculateRoundScoreDelta(playerStates, List.of("Alice"), RoundEndCondition.GOING_OUT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("훌라 승자는 패자 총합의 4배를 받는다")
        void hula_winner_gets_4x_multiplier() {
            PlayerRoundState alice = new PlayerRoundState();
            alice.setPlayerId("Alice");
            alice.setHand(List.of());
            alice.setHadMeldsAtTurnStart(false); // 훌라

            PlayerRoundState bob = new PlayerRoundState();
            bob.setPlayerId("Bob");
            bob.setHand(List.of(new Card(Suit.SPADE, Rank.FIVE))); // 5점
            bob.setHadMeldsAtTurnStart(false);

            Map<String, PlayerRoundState> states = Map.of("Alice", alice, "Bob", bob);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("Alice"), RoundEndCondition.GOING_OUT);

            // Bob 손패 5점 × 4배 = 20
            assertThat(delta.get("Alice")).isEqualTo(20);
            assertThat(delta.get("Bob")).isEqualTo(-5);
        }
    }
}
