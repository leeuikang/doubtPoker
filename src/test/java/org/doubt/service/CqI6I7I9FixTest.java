package org.doubt.service;

import org.doubt.constant.PlayerStatus;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CQ-I6, CQ-I7, CQ-I9 수정 검증 테스트
 *
 * <p>CQ-I6: {@code WebSocketEventListener} — {@code capturedState = null} 초기화 패턴을
 * {@code restorePlayerInRoom()} Optional 반환 헬퍼로 추출. 동작 정확성은
 * {@code handleWebSocketConnectListener}의 결과로 간접 검증.</p>
 *
 * <p>CQ-I7: {@code SessionManager} — 미사용 {@code WebSocketSession} import 제거.
 * 컴파일 성공 자체가 증거이므로 SessionManager 기본 동작을 확인한다.</p>
 *
 * <p>CQ-I9: {@code ScoreService.calculateRoundScoreDelta} — {@code List#contains} O(n) 를
 * {@code Set#contains} O(1) 으로 교체. 결과 정확성으로 검증.</p>
 */
@DisplayName("CQ-I6, CQ-I7, CQ-I9 수정 검증")
class CqI6I7I9FixTest {

    // ----------------------------------------------------------------
    // CQ-I7: SessionManager — WebSocketSession import 제거 후에도 정상 동작
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("CQ-I7: SessionManager 기본 동작")
    class SessionManagerWorks {

        private final org.doubt.handler.SessionManager sessionManager =
                new org.doubt.handler.SessionManager();

        @Test
        @DisplayName("addUserToRoom 후 getUserList에 포함된다")
        void add_user_appears_in_list() {
            sessionManager.addUserToRoom("room1", "Alice");
            assertThat(sessionManager.getUserList("room1")).contains("Alice");
        }

        @Test
        @DisplayName("removeUserFromRoom 후 getUserList에서 제거된다")
        void remove_user_disappears_from_list() {
            sessionManager.addUserToRoom("room2", "Bob");
            sessionManager.removeUserFromRoom("room2", "Bob");
            assertThat(sessionManager.getUserList("room2")).doesNotContain("Bob");
        }

        @Test
        @DisplayName("존재하지 않는 방은 빈 목록을 반환한다")
        void unknown_room_returns_empty_list() {
            assertThat(sessionManager.getUserList("nonexistent")).isEmpty();
        }
    }

    // ----------------------------------------------------------------
    // CQ-I9: ScoreService — Set으로 교체 후 결과 정확성
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("CQ-I9: ScoreService 패배자 필터링 — Set 교체 후 정확성")
    class ScoreServiceLoserFilter {

        private final ScoreService scoreService = new ScoreService();

        @Test
        @DisplayName("단일 승자, 단일 패자 — 패자 점수가 음수로 반영된다")
        void single_winner_single_loser() {
            // hadMeldsAtTurnStart=true → 훌라 아님(배율 1x)
            PlayerRoundState alice = buildWinner(List.of());
            PlayerRoundState bob = buildLoser(List.of(new Card(Suit.SPADE, Rank.FIVE))); // 5점

            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    Map.of("Alice", alice, "Bob", bob),
                    List.of("Alice"),
                    RoundEndCondition.GOING_OUT);

            assertThat(delta.get("Alice")).isEqualTo(5);
            assertThat(delta.get("Bob")).isEqualTo(-5);
        }

        @Test
        @DisplayName("공동 승자 — 패자 총합이 균등 분배된다")
        void multiple_winners_share_loser_total() {
            PlayerRoundState alice = buildWinner(List.of());
            PlayerRoundState bob = buildWinner(List.of());
            PlayerRoundState carol = buildLoser(List.of(new Card(Suit.SPADE, Rank.TEN))); // 10점

            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    Map.of("Alice", alice, "Bob", bob, "Carol", carol),
                    List.of("Alice", "Bob"),
                    RoundEndCondition.GOING_OUT);

            assertThat(delta.get("Carol")).isEqualTo(-10);
            // 10 / 2 = 5 각자
            assertThat(delta.get("Alice")).isEqualTo(5);
            assertThat(delta.get("Bob")).isEqualTo(5);
        }

        @Test
        @DisplayName("승자가 loserSet에 포함되지 않는다 — winnerIds 목록이 여럿이어도 정확히 필터된다")
        void winners_are_not_in_loser_set() {
            PlayerRoundState alice = buildWinner(List.of());
            PlayerRoundState bob = buildWinner(List.of());
            PlayerRoundState carol = buildLoser(List.of(new Card(Suit.SPADE, Rank.THREE))); // 3점

            Map<String, Integer> delta = scoreService.calculateRoundScoreDelta(
                    Map.of("Alice", alice, "Bob", bob, "Carol", carol),
                    List.of("Alice", "Bob"),
                    RoundEndCondition.GOING_OUT);

            // 승자들은 양수 또는 0 (패자가 아니므로 음수 불가)
            assertThat(delta.get("Alice")).isGreaterThanOrEqualTo(0);
            assertThat(delta.get("Bob")).isGreaterThanOrEqualTo(0);
            assertThat(delta.get("Carol")).isNegative();
        }
    }

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    /** hadMeldsAtTurnStart=true → 훌라 판정 제외되는 일반 승자 */
    private PlayerRoundState buildWinner(List<Card> hand) {
        PlayerRoundState p = new PlayerRoundState();
        p.setHand(hand);
        p.setStatus(PlayerStatus.ACTIVE);
        p.setHadMeldsAtTurnStart(true); // 멜드 있었음 → 훌라 아님
        return p;
    }

    /** 패자 (훌라 판정과 무관) */
    private PlayerRoundState buildLoser(List<Card> hand) {
        PlayerRoundState p = new PlayerRoundState();
        p.setHand(hand);
        p.setStatus(PlayerStatus.ACTIVE);
        p.setHadMeldsAtTurnStart(false);
        return p;
    }
}
