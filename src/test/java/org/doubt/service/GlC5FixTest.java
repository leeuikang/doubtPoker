package org.doubt.service;

import org.doubt.constant.MeldType;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.Rank;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Suit;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;
import org.doubt.dto.Meld;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.doubt.dto.request.DiscardRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GL-C5 버그 수정 검증 테스트
 * hadMeldsAtTurnStart 플래그가 advanceTurn/handleThankYou 에서 올바르게 세팅되며,
 * ScoreService 가 이 플래그를 기반으로 훌라 여부를 정확히 판정하는지 확인
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GL-C5: hadMeldsAtTurnStart 기반 훌라 판정")
class GlC5FixTest {

    @Mock
    private DeckService deckService;

    @Mock
    private MeldValidationService meldValidationService;

    @Mock
    private ScoreService scoreService;

    @InjectMocks
    private RoundService roundService;

    private ScoreService realScoreService;

    @BeforeEach
    void setUp() {
        realScoreService = new ScoreService();
    }

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private PlayerRoundState makePlayerState(String playerId, List<Card> hand) {
        PlayerRoundState prs = new PlayerRoundState();
        prs.setPlayerId(playerId);
        prs.setHand(new ArrayList<>(hand));
        prs.setStatus(PlayerStatus.ACTIVE);
        prs.setHasMeldedThisTurn(false);
        prs.setHadMeldsAtTurnStart(false);
        prs.setHasDeclaredStop(false);
        prs.setHasBankrupted(false);
        prs.setDisconnectCount(0);
        return prs;
    }

    /**
     * A가 현재 플레이어(index=0), B가 다음 플레이어인 2인 RoundState 생성.
     * A의 핸드는 discardable 카드 2장(버리고 나서 1장 남아야 하므로), B의 핸드는 3장.
     */
    private RoundState buildTwoPlayerState(String playerA, String playerB,
                                            List<Card> handA, List<Card> handB,
                                            List<Meld> tableMelds) {
        RoundState state = new RoundState();
        state.setTurnOrder(new ArrayList<>(List.of(playerA, playerB)));
        state.setCurrentPlayerIndex(0);
        state.setTurnPhase(TurnPhase.ACTION);
        state.setStockPile(new ArrayList<>(List.of(
                new Card(Suit.CLUB, Rank.TWO),
                new Card(Suit.CLUB, Rank.THREE),
                new Card(Suit.CLUB, Rank.FOUR),
                new Card(Suit.CLUB, Rank.FIVE),
                new Card(Suit.CLUB, Rank.SIX)
        )));
        state.setDiscardPile(new ArrayList<>());
        state.setTableMelds(tableMelds != null ? new ArrayList<>(tableMelds) : new ArrayList<>());
        state.setStockRefillCount(0);
        state.setEndCondition(null);
        state.setLastDiscardPlayerId(null);
        state.setThankYouTimerSec(0);

        Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
        playerStates.put(playerA, makePlayerState(playerA, handA));
        playerStates.put(playerB, makePlayerState(playerB, handB));
        state.setPlayerStates(playerStates);
        return state;
    }

    /** STRAIGHT [5♥,6♥,7♥] 멜드를 ownerId 소유로 생성 */
    private Meld buildStraightMeld(String meldId, String ownerId) {
        List<Card> actualCards = List.of(
                new Card(Suit.HEART, Rank.FIVE),
                new Card(Suit.HEART, Rank.SIX),
                new Card(Suit.HEART, Rank.SEVEN)
        );
        List<DeclaredCard> declaredCards = List.of(
                new DeclaredCard(Suit.HEART, Rank.FIVE),
                new DeclaredCard(Suit.HEART, Rank.SIX),
                new DeclaredCard(Suit.HEART, Rank.SEVEN)
        );
        return Meld.create(meldId, ownerId, MeldType.STRAIGHT, actualCards, declaredCards, false);
    }

    // ================================================================
    // advanceTurn 에 의한 hadMeldsAtTurnStart 세팅 검증
    // ================================================================

    @Nested
    @DisplayName("advanceTurn — 다음 플레이어 hadMeldsAtTurnStart 세팅")
    class AdvanceTurnHadMeldsAtTurnStart {

        private static final String PLAYER_A = "playerA";
        private static final String PLAYER_B = "playerB";

        @Test
        @DisplayName("다음 플레이어(B)에게 테이블에 B 소유 멜드가 있으면 hadMeldsAtTurnStart=true")
        void advanceTurn_setsHadMeldsAtTurnStart_whenNextPlayerHasMelds() {
            // B 소유 멜드를 테이블에 올림
            Meld bMeld = buildStraightMeld("meld-b", PLAYER_B);
            List<Card> handA = new ArrayList<>(List.of(
                    new Card(Suit.SPADE, Rank.ACE),
                    new Card(Suit.SPADE, Rank.TWO)
            ));
            List<Card> handB = new ArrayList<>(List.of(
                    new Card(Suit.DIAMOND, Rank.ACE),
                    new Card(Suit.DIAMOND, Rank.TWO),
                    new Card(Suit.DIAMOND, Rank.THREE)
            ));

            RoundState state = buildTwoPlayerState(PLAYER_A, PLAYER_B, handA, handB, List.of(bMeld));

            // A가 카드를 버려 턴이 B로 넘어감 (advanceTurn 호출)
            Card discard = handA.get(0);
            roundService.handleDiscard(state, PLAYER_A, new DiscardRequest(discard));

            // 이제 B가 현재 플레이어
            PlayerRoundState bState = state.getPlayerStates().get(PLAYER_B);
            assertThat(bState.isHadMeldsAtTurnStart()).isTrue();
        }

        @Test
        @DisplayName("다음 플레이어(B)에게 테이블에 B 소유 멜드가 없으면 hadMeldsAtTurnStart=false")
        void advanceTurn_setsHadMeldsAtTurnStart_false_whenNextPlayerHasNoMelds() {
            // 테이블에 멜드 없음
            List<Card> handA = new ArrayList<>(List.of(
                    new Card(Suit.SPADE, Rank.ACE),
                    new Card(Suit.SPADE, Rank.TWO)
            ));
            List<Card> handB = new ArrayList<>(List.of(
                    new Card(Suit.DIAMOND, Rank.ACE),
                    new Card(Suit.DIAMOND, Rank.TWO),
                    new Card(Suit.DIAMOND, Rank.THREE)
            ));

            RoundState state = buildTwoPlayerState(PLAYER_A, PLAYER_B, handA, handB, null);

            Card discard = handA.get(0);
            roundService.handleDiscard(state, PLAYER_A, new DiscardRequest(discard));

            PlayerRoundState bState = state.getPlayerStates().get(PLAYER_B);
            assertThat(bState.isHadMeldsAtTurnStart()).isFalse();
        }
    }

    // ================================================================
    // ScoreService.calculateRoundScoreDelta — 훌라 판정
    // ================================================================

    @Nested
    @DisplayName("calculateRoundScoreDelta — 훌라 배율 적용 여부")
    class HulaScoreCalculation {

        private static final String WINNER = "winner";
        private static final String LOSER = "loser";

        private Map<String, PlayerRoundState> buildPlayerStates(
                boolean winnerHadMelds, List<Card> winnerHand, List<Card> loserHand) {
            PlayerRoundState winnerState = new PlayerRoundState();
            winnerState.setPlayerId(WINNER);
            winnerState.setHand(new ArrayList<>(winnerHand));
            winnerState.setHadMeldsAtTurnStart(winnerHadMelds);
            winnerState.setHasDeclaredStop(false);
            winnerState.setHasBankrupted(false);
            winnerState.setStatus(PlayerStatus.ACTIVE);

            PlayerRoundState loserState = new PlayerRoundState();
            loserState.setPlayerId(LOSER);
            loserState.setHand(new ArrayList<>(loserHand));
            loserState.setHadMeldsAtTurnStart(false);
            loserState.setHasDeclaredStop(false);
            loserState.setHasBankrupted(false);
            loserState.setStatus(PlayerStatus.ACTIVE);

            Map<String, PlayerRoundState> states = new LinkedHashMap<>();
            states.put(WINNER, winnerState);
            states.put(LOSER, loserState);
            return states;
        }

        @Test
        @DisplayName("승자의 hadMeldsAtTurnStart=false + GOING_OUT → 훌라 배율(x4) 적용")
        void calculateRoundScoreDelta_hulaEligible_whenWinnerHadNoMeldsAtTurnStart() {
            // 패배자 손패: A(1점) → baseScore=1, multiplier=1 → loserScore=1
            // 훌라 배율 적용: winnerGain = 1 * 4 = 4
            List<Card> winnerHand = new ArrayList<>(); // 고잉아웃 후 빈 손
            List<Card> loserHand = List.of(new Card(Suit.SPADE, Rank.ACE)); // 1점

            Map<String, PlayerRoundState> states = buildPlayerStates(false, winnerHand, loserHand);

            Map<String, Integer> delta = realScoreService.calculateRoundScoreDelta(
                    states, List.of(WINNER), RoundEndCondition.GOING_OUT);

            // 패배자 점수: -1
            assertThat(delta.get(LOSER)).isEqualTo(-1);
            // 승자 점수: 1 * 4 = 4 (훌라)
            assertThat(delta.get(WINNER)).isEqualTo(4);
        }

        @Test
        @DisplayName("승자의 hadMeldsAtTurnStart=true + GOING_OUT → 훌라 미적용, 패배자 합산만 획득")
        void calculateRoundScoreDelta_notHula_whenWinnerHadMeldsAtTurnStart() {
            List<Card> winnerHand = new ArrayList<>();
            List<Card> loserHand = List.of(new Card(Suit.SPADE, Rank.ACE)); // 1점

            Map<String, PlayerRoundState> states = buildPlayerStates(true, winnerHand, loserHand);

            Map<String, Integer> delta = realScoreService.calculateRoundScoreDelta(
                    states, List.of(WINNER), RoundEndCondition.GOING_OUT);

            // 패배자 점수: -1
            assertThat(delta.get(LOSER)).isEqualTo(-1);
            // 승자 점수: 1 (훌라 없음)
            assertThat(delta.get(WINNER)).isEqualTo(1);
        }

        @Test
        @DisplayName("GL-C5 원래 버그: 이전 턴에 멜드가 있었으나 지목으로 회수된 경우 — hadMeldsAtTurnStart=false이면 훌라 적용")
        void calculateRoundScoreDelta_hulaEligible_afterDoubtRecovery() {
            // 현재 턴 시작 시점에 지목으로 멜드가 모두 회수되어 hadMeldsAtTurnStart=false인 상태
            List<Card> winnerHand = new ArrayList<>();
            List<Card> loserHand = List.of(
                    new Card(Suit.SPADE, Rank.TWO),  // 2점
                    new Card(Suit.SPADE, Rank.THREE) // 3점
            ); // loserScore = 5

            PlayerRoundState winnerState = new PlayerRoundState();
            winnerState.setPlayerId(WINNER);
            winnerState.setHand(new ArrayList<>(winnerHand));
            winnerState.setHadMeldsAtTurnStart(false);  // 핵심: 현재 턴 시작 시 멜드 없음
            winnerState.setHasDeclaredStop(false);
            winnerState.setHasBankrupted(false);
            winnerState.setStatus(PlayerStatus.ACTIVE);

            PlayerRoundState loserState = new PlayerRoundState();
            loserState.setPlayerId(LOSER);
            loserState.setHand(new ArrayList<>(loserHand));
            loserState.setHadMeldsAtTurnStart(false);
            loserState.setHasDeclaredStop(false);
            loserState.setHasBankrupted(false);
            loserState.setStatus(PlayerStatus.ACTIVE);

            Map<String, PlayerRoundState> states = new LinkedHashMap<>();
            states.put(WINNER, winnerState);
            states.put(LOSER, loserState);

            Map<String, Integer> delta = realScoreService.calculateRoundScoreDelta(
                    states, List.of(WINNER), RoundEndCondition.GOING_OUT);

            // GL-C5 수정: hasEverMelded=true여도 hadMeldsAtTurnStart=false이면 훌라 적용
            // loserScore=5, 훌라 x4 → winnerGain=20
            assertThat(delta.get(LOSER)).isEqualTo(-5);
            assertThat(delta.get(WINNER)).isEqualTo(20);
        }
    }
}
