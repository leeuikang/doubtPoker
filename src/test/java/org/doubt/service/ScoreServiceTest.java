package org.doubt.service;

import org.doubt.constant.PlayerStatus;
import org.doubt.constant.Rank;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.TournamentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScoreServiceTest {

    private ScoreService scoreService;

    @BeforeEach
    void setUp() {
        scoreService = new ScoreService();
    }

    // --- 헬퍼 ---

    private Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    private PlayerRoundState makeState(String playerId, List<Card> hand,
                                       boolean hasDeclaredStop, boolean hasBankrupted,
                                       boolean hasEverMelded) {
        PlayerRoundState state = new PlayerRoundState();
        state.setPlayerId(playerId);
        state.setHand(hand);
        state.setHasDeclaredStop(hasDeclaredStop);
        state.setHasBankrupted(hasBankrupted);
        state.setHasEverMelded(hasEverMelded);
        state.setStatus(PlayerStatus.ACTIVE);
        return state;
    }

    private TournamentState newTournament(String roomId, Map<String, Integer> initialScores) {
        TournamentState t = new TournamentState(roomId);
        t.setScores(new HashMap<>(initialScores));
        t.setEliminatedPlayers(new HashSet<>());
        t.setRoundHistory(new ArrayList<>());
        return t;
    }

    // ==================== calculateHandScore ====================

    @Nested
    @DisplayName("calculateHandScore")
    class CalculateHandScore {

        @Test
        @DisplayName("빈 손패 점수는 0이다")
        void empty_hand_score_is_zero() {
            assertEquals(0, scoreService.calculateHandScore(List.of()));
        }

        @Test
        @DisplayName("ACE는 1점이다")
        void ace_scores_one_point() {
            List<Card> hand = List.of(card(Suit.SPADE, Rank.ACE));
            assertEquals(1, scoreService.calculateHandScore(hand));
        }

        @Test
        @DisplayName("SEVEN은 핸드에 남을 때 14점이다")
        void seven_in_hand_scores_14_points() {
            List<Card> hand = List.of(card(Suit.HEART, Rank.SEVEN));
            assertEquals(14, scoreService.calculateHandScore(hand));
        }

        @Test
        @DisplayName("JACK=11, QUEEN=12, KING=13 점이다")
        void face_cards_score_correctly() {
            List<Card> hand = List.of(
                    card(Suit.SPADE, Rank.JACK),
                    card(Suit.HEART, Rank.QUEEN),
                    card(Suit.DIAMOND, Rank.KING)
            );
            assertEquals(11 + 12 + 13, scoreService.calculateHandScore(hand));
        }

        @Test
        @DisplayName("숫자 카드는 기본점수(표시값)로 계산한다")
        void number_cards_score_base_point() {
            List<Card> hand = List.of(
                    card(Suit.SPADE, Rank.TWO),
                    card(Suit.HEART, Rank.THREE),
                    card(Suit.DIAMOND, Rank.FOUR),
                    card(Suit.CLUB, Rank.FIVE),
                    card(Suit.SPADE, Rank.SIX)
            );
            assertEquals(2 + 3 + 4 + 5 + 6, scoreService.calculateHandScore(hand));
        }

        @Test
        @DisplayName("SEVEN이 포함된 복합 손패 점수를 올바르게 계산한다")
        void hand_with_seven_calculates_correctly() {
            List<Card> hand = List.of(
                    card(Suit.SPADE, Rank.SEVEN),  // 14점
                    card(Suit.HEART, Rank.KING),    // 13점
                    card(Suit.DIAMOND, Rank.ACE)    // 1점
            );
            assertEquals(14 + 13 + 1, scoreService.calculateHandScore(hand));
        }
    }

    // ==================== calculateRoundScoreDelta ====================

    @Nested
    @DisplayName("calculateRoundScoreDelta - 기본 승패")
    class CalculateRoundScoreDeltaBasic {

        @Test
        @DisplayName("단일 승자, 단일 패자: 기본 1배 적용")
        void single_winner_single_loser_base_multiplier() {
            // 패자 손패 10점
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.TEN)), // 10점
                    false, false, false);

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.GOING_OUT);

            assertEquals(10, delta.get("w1"),  "승자는 +10을 얻어야 한다");
            assertEquals(-10, delta.get("l1"), "패자는 -10을 잃어야 한다");
        }

        @Test
        @DisplayName("공동 승자는 총 패자 점수를 균등 분배한다")
        void co_winners_split_equally() {
            PlayerRoundState w1 = makeState("w1", List.of(), false, false, true);
            PlayerRoundState w2 = makeState("w2", List.of(), false, false, true);
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.TEN), card(Suit.HEART, Rank.TEN)), // 20점
                    false, false, false);

            Map<String, PlayerRoundState> states = Map.of("w1", w1, "w2", w2, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1", "w2"), RoundEndCondition.GOING_OUT);

            assertEquals(10, delta.get("w1"), "공동 승자 1은 +10을 얻어야 한다");
            assertEquals(10, delta.get("w2"), "공동 승자 2는 +10을 얻어야 한다");
            assertEquals(-20, delta.get("l1"), "패자는 -20을 잃어야 한다");
        }

        @Test
        @DisplayName("패자 여러 명: 각 패자 점수를 각자 부담하고 승자는 합계를 받는다")
        void multiple_losers_winner_gets_total() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            PlayerRoundState loser1 = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE)), // 5점
                    false, false, false);
            PlayerRoundState loser2 = makeState("l2",
                    List.of(card(Suit.HEART, Rank.THREE)), // 3점
                    false, false, false);

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser1, "l2", loser2);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.GOING_OUT);

            assertEquals(8, delta.get("w1"),  "승자는 패자 합계 8점을 얻어야 한다");
            assertEquals(-5, delta.get("l1"), "패자1은 -5여야 한다");
            assertEquals(-3, delta.get("l2"), "패자2는 -3이어야 한다");
        }
    }

    @Nested
    @DisplayName("calculateRoundScoreDelta - STOP 배율")
    class CalculateRoundScoreDeltaStop {

        @Test
        @DisplayName("STOP 조건에서 hasDeclaredStop=true 패자는 2배 점수를 잃는다")
        void stop_condition_declared_stop_loser_doubles_penalty() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            // 손패 5점, STOP 선언자
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE)),
                    true, false, false); // hasDeclaredStop=true

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.STOP);

            assertEquals(-10, delta.get("l1"), "STOP 선언 패자는 5 * 2 = 10점을 잃어야 한다");
            assertEquals(10, delta.get("w1"));
        }

        @Test
        @DisplayName("STOP 조건에서 hasDeclaredStop=false 패자는 기본 1배 적용된다")
        void stop_condition_non_declared_stop_loser_base_multiplier() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE)),
                    false, false, false); // hasDeclaredStop=false

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.STOP);

            assertEquals(-5, delta.get("l1"), "STOP 미선언 패자는 기본 1배 5점을 잃어야 한다");
        }

        @Test
        @DisplayName("GOING_OUT 조건에서는 hasDeclaredStop=true여도 배율이 적용되지 않는다")
        void going_out_condition_no_stop_multiplier_even_if_declared() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE)),
                    true, false, false); // hasDeclaredStop=true 이지만 endCondition은 GOING_OUT

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.GOING_OUT);

            assertEquals(-5, delta.get("l1"), "GOING_OUT에서는 STOP 배율 없이 기본 1배여야 한다");
        }
    }

    @Nested
    @DisplayName("calculateRoundScoreDelta - SEVEN 보유 배율")
    class CalculateRoundScoreDeltaSeven {

        @Test
        @DisplayName("패자 손패에 7이 있으면 배율 +1 (2배 패널티)")
        void loser_with_seven_in_hand_doubles_penalty() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            // 손패: KING(13점) + SEVEN(14점) = 27점, 배율 2배 = 54점 패널티
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.KING), card(Suit.HEART, Rank.SEVEN)),
                    false, false, false);

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.GOING_OUT);

            assertEquals(-(13 + 14) * 2, delta.get("l1"), "7 보유 패자는 2배 패널티를 받아야 한다");
        }

        @Test
        @DisplayName("STOP + SEVEN 동시: 배율 3배 (1 + 1 + 1)")
        void stop_and_seven_triple_multiplier() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            // 손패: FIVE(5점) + SEVEN(14점) = 19점, STOP 선언, 7 보유 => 배율 3배
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE), card(Suit.HEART, Rank.SEVEN)),
                    true, false, false); // STOP 선언

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.STOP);

            assertEquals(-(5 + 14) * 3, delta.get("l1"), "STOP+SEVEN 패자는 3배 패널티를 받아야 한다");
        }
    }

    @Nested
    @DisplayName("calculateRoundScoreDelta - 파산(BANKRUPTCY)")
    class CalculateRoundScoreDeltaBankruptcy {

        @Test
        @DisplayName("파산자 페널티: max(본인핸드*2, 다른 패배자 최고 조정점수)")
        void bankrupt_penalty_is_max_of_doubled_own_or_max_other_loser() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            // 일반 패자: 손패 10점 -> 조정점수 10점
            PlayerRoundState normalLoser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.TEN)),
                    false, false, false);
            // 파산자: 손패 4점 -> 본인*2 = 8점 < 다른 패자 최고 10점 -> 10점 적용
            PlayerRoundState bankruptLoser = makeState("l2",
                    List.of(card(Suit.HEART, Rank.FOUR)),
                    false, true, false); // hasBankrupted=true

            Map<String, PlayerRoundState> states = Map.of(
                    "w1", winner, "l1", normalLoser, "l2", bankruptLoser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.BANKRUPTCY);

            assertEquals(-10, delta.get("l1"), "일반 패자는 -10이어야 한다");
            assertEquals(-10, delta.get("l2"), "파산자는 max(4*2=8, 10)=10이므로 -10이어야 한다");
            assertEquals(20, delta.get("w1"),  "승자는 합계 20을 얻어야 한다");
        }

        @Test
        @DisplayName("파산자 페널티: 본인*2가 다른 패자 최고점보다 클 때 본인*2 적용")
        void bankrupt_penalty_uses_own_doubled_when_higher() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            // 일반 패자: 손패 3점 -> 조정점수 3점
            PlayerRoundState normalLoser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.THREE)),
                    false, false, false);
            // 파산자: 손패 10점 -> 본인*2 = 20 > 다른 패자 최고 3점 -> 20점 적용
            PlayerRoundState bankruptLoser = makeState("l2",
                    List.of(card(Suit.HEART, Rank.TEN)),
                    false, true, false);

            Map<String, PlayerRoundState> states = Map.of(
                    "w1", winner, "l1", normalLoser, "l2", bankruptLoser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.BANKRUPTCY);

            assertEquals(-3, delta.get("l1"),  "일반 패자는 -3이어야 한다");
            assertEquals(-20, delta.get("l2"), "파산자는 max(10*2=20, 3)=20이므로 -20이어야 한다");
            assertEquals(23, delta.get("w1"),  "승자는 합계 23을 얻어야 한다");
        }

        @Test
        @DisplayName("파산자만 있을 때 다른 패자 최고 점수가 0이면 본인*2 적용")
        void bankrupt_only_loser_uses_doubled_own_score() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true);
            PlayerRoundState bankruptLoser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE)),
                    false, true, false); // 5점, 파산

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", bankruptLoser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.BANKRUPTCY);

            // 다른 패자 없으므로 maxOtherLoser=0, 5*2=10 > 0 => 10
            assertEquals(-10, delta.get("l1"), "파산자 혼자일 때 max(5*2=10, 0)=10이므로 -10이어야 한다");
            assertEquals(10, delta.get("w1"));
        }
    }

    @Nested
    @DisplayName("calculateRoundScoreDelta - 훌라(HULA)")
    class CalculateRoundScoreDeltaHula {

        @Test
        @DisplayName("GOING_OUT이고 승자가 멜드를 전혀 안 했으면 훌라: 패자 합계의 4배")
        void hula_winner_gets_four_times_loser_total() {
            // 승자: 멜드한 적 없음 (HULA 조건)
            PlayerRoundState winner = makeState("w1", List.of(), false, false, false); // hasEverMelded=false
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE)), // 5점
                    false, false, false);

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.GOING_OUT);

            assertEquals(5 * 4, delta.get("w1"), "훌라 승자는 패자 합계 5점의 4배(=20)를 얻어야 한다");
            assertEquals(-5, delta.get("l1"));
        }

        @Test
        @DisplayName("GOING_OUT이고 승자가 멜드를 했으면 훌라 아님: 패자 합계 1배")
        void non_hula_winner_gets_base_loser_total() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, true); // hasEverMelded=true
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE)),
                    false, false, false);

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.GOING_OUT);

            assertEquals(5, delta.get("w1"), "일반 승자는 패자 합계 5점을 얻어야 한다");
            assertEquals(-5, delta.get("l1"));
        }

        @Test
        @DisplayName("STOP 조건에서 멜드 미사용이어도 훌라가 아니다")
        void stop_condition_not_hula_even_without_meld() {
            PlayerRoundState winner = makeState("w1", List.of(), false, false, false); // hasEverMelded=false
            PlayerRoundState loser = makeState("l1",
                    List.of(card(Suit.SPADE, Rank.FIVE)),
                    false, false, false);

            Map<String, PlayerRoundState> states = Map.of("w1", winner, "l1", loser);
            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    states, List.of("w1"), RoundEndCondition.STOP);

            assertEquals(5, delta.get("w1"), "STOP 조건에서는 훌라가 없다");
        }
    }

    // ==================== applyScores ====================

    @Nested
    @DisplayName("applyScores")
    class ApplyScores {

        @Test
        @DisplayName("점수 델타가 기존 토너먼트 점수에 올바르게 반영된다")
        void score_delta_applied_to_tournament_scores() {
            TournamentState tournament = newTournament("room1",
                    Map.of("p1", 100, "p2", 100));

            Map<String, Integer> delta = Map.of("p1", 10, "p2", -10);
            scoreService.applyScores(tournament, delta);

            assertEquals(110, tournament.getScores().get("p1"));
            assertEquals(90, tournament.getScores().get("p2"));
        }

        @Test
        @DisplayName("점수가 0 이하가 되면 eliminatedPlayers에 추가된다")
        void player_with_score_zero_or_below_is_eliminated() {
            TournamentState tournament = newTournament("room1",
                    Map.of("p1", 100, "p2", 5));

            Map<String, Integer> delta = Map.of("p1", 5, "p2", -5);
            scoreService.applyScores(tournament, delta);

            assertFalse(tournament.getEliminatedPlayers().contains("p1"),
                    "p1은 탈락하지 않아야 한다");
            assertTrue(tournament.getEliminatedPlayers().contains("p2"),
                    "p2는 점수가 0이므로 탈락해야 한다");
        }

        @Test
        @DisplayName("점수가 음수가 되면 eliminatedPlayers에 추가된다")
        void player_with_negative_score_is_eliminated() {
            TournamentState tournament = newTournament("room1",
                    Map.of("p1", 3));

            Map<String, Integer> delta = Map.of("p1", -10);
            scoreService.applyScores(tournament, delta);

            assertEquals(-7, tournament.getScores().get("p1"));
            assertTrue(tournament.getEliminatedPlayers().contains("p1"));
        }

        @Test
        @DisplayName("이미 탈락한 플레이어는 중복 추가되지 않는다")
        void already_eliminated_player_not_added_twice() {
            TournamentState tournament = newTournament("room1",
                    Map.of("p1", -5));
            tournament.getEliminatedPlayers().add("p1"); // 이미 탈락

            Map<String, Integer> delta = Map.of("p1", -5);
            scoreService.applyScores(tournament, delta);

            long count = tournament.getEliminatedPlayers().stream()
                    .filter(id -> id.equals("p1")).count();
            assertEquals(1, count, "중복 탈락 추가가 없어야 한다");
        }

        @Test
        @DisplayName("라운드 히스토리에 이번 라운드 델타가 기록된다")
        void round_delta_is_added_to_round_history() {
            TournamentState tournament = newTournament("room1",
                    Map.of("p1", 100, "p2", 100));

            Map<String, Integer> delta = Map.of("p1", 15, "p2", -15);
            scoreService.applyScores(tournament, delta);

            assertEquals(1, tournament.getRoundHistory().size());
            assertEquals(15, tournament.getRoundHistory().get(0).get("p1"));
            assertEquals(-15, tournament.getRoundHistory().get(0).get("p2"));
        }

        @Test
        @DisplayName("여러 라운드 적용 시 roundHistory에 순서대로 기록된다")
        void multiple_rounds_recorded_in_order() {
            TournamentState tournament = newTournament("room1",
                    Map.of("p1", 100, "p2", 100));

            scoreService.applyScores(tournament, Map.of("p1", 10, "p2", -10));
            scoreService.applyScores(tournament, Map.of("p1", -5, "p2", 5));

            assertEquals(2, tournament.getRoundHistory().size());
            assertEquals(10, tournament.getRoundHistory().get(0).get("p1"));
            assertEquals(-5, tournament.getRoundHistory().get(1).get("p1"));
        }

        @Test
        @DisplayName("기존 토너먼트 점수가 없는 플레이어는 0을 기준으로 델타 적용된다")
        void new_player_score_starts_from_zero() {
            TournamentState tournament = newTournament("room1", new HashMap<>());

            Map<String, Integer> delta = Map.of("p1", 50);
            scoreService.applyScores(tournament, delta);

            assertEquals(50, tournament.getScores().get("p1"));
        }

        @Test
        @DisplayName("applyScores는 업데이트된 TournamentState를 반환한다")
        void apply_scores_returns_updated_tournament_state() {
            TournamentState tournament = newTournament("room1",
                    Map.of("p1", 100));

            Map<String, Integer> delta = Map.of("p1", 20);
            TournamentState result = scoreService.applyScores(tournament, delta);

            assertSame(tournament, result, "동일한 TournamentState 객체가 반환되어야 한다");
            assertEquals(120, result.getScores().get("p1"));
        }
    }
}
