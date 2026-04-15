package org.doubt.service;

import org.doubt.constant.PlayerStatus;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GL-W1 버그 수정 검증 테스트
 *
 * advanceTurn 내 루프 종료 후 nextIndex 가 ACTIVE 플레이어를 가리키는지 재확인하는
 * 안전장치(guard) 가 올바르게 동작하는지 검증한다.
 *
 * 수정 내용:
 *   - 루프 후 nextIndex 플레이어가 ACTIVE 가 아닌 경우
 *     → endCondition 이 null 이면 BANKRUPTCY 강제 설정 후 return (currentPlayerIndex 불변)
 *   - endCondition 이 이미 설정된 경우 → 덮어쓰지 않고 return
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GL-W1: advanceTurn — 모든 플레이어 ELIMINATED 시 안전장치")
class GlW1FixTest {

    @Mock
    private DeckService deckService;

    @Mock
    private MeldValidationService meldValidationService;

    @Mock
    private ScoreService scoreService;

    @InjectMocks
    private RoundService roundService;

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    /** 지정한 수만큼 고유 카드를 생성한다 (중복 없음). */
    private List<Card> makeCards(int count) {
        List<Card> cards = new ArrayList<>();
        Suit[] suits = Suit.values();
        Rank[] ranks = Rank.values();
        int idx = 0;
        for (int i = 0; i < count; i++) {
            cards.add(new Card(suits[idx % suits.length], ranks[idx % ranks.length]));
            idx++;
        }
        return cards;
    }

    /**
     * PlayerRoundState 를 생성한다.
     *
     * @param playerId 플레이어 ID
     * @param hand     손패
     * @param status   플레이어 상태
     */
    private PlayerRoundState makePlayerState(String playerId, List<Card> hand, PlayerStatus status) {
        PlayerRoundState prs = new PlayerRoundState();
        prs.setPlayerId(playerId);
        prs.setHand(new ArrayList<>(hand));
        prs.setStatus(status);
        prs.setHasMeldedThisTurn(false);
        prs.setHadMeldsAtTurnStart(false);
        prs.setHasDeclaredStop(false);
        prs.setHasBankrupted(false);
        prs.setDisconnectCount(0);
        return prs;
    }

    /**
     * 플레이어 목록, 턴 순서, 각 플레이어 상태를 포함하는 RoundState 를 조립한다.
     *
     * @param playerInfos 플레이어 ID → (손패, 상태) 매핑 (순서 보장: LinkedHashMap)
     * @param currentIdx  현재 플레이어 인덱스
     * @param phase       현재 턴 페이즈
     */
    private RoundState buildState(List<String> turnOrder,
                                  Map<String, PlayerStatus> statusMap,
                                  Map<String, List<Card>> handMap,
                                  int currentIdx,
                                  TurnPhase phase,
                                  List<Card> stock,
                                  List<Card> discard) {
        RoundState state = new RoundState();
        state.setTurnOrder(new ArrayList<>(turnOrder));
        state.setCurrentPlayerIndex(currentIdx);
        state.setTurnPhase(phase);
        state.setStockPile(new ArrayList<>(stock));
        state.setDiscardPile(new ArrayList<>(discard));
        state.setTableMelds(new ArrayList<>());
        state.setStockRefillCount(0);
        state.setEndCondition(null);
        state.setLastDiscardPlayerId(null);
        state.setThankYouTimerSec(0);

        Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
        for (String id : turnOrder) {
            playerStates.put(id,
                    makePlayerState(id, handMap.get(id), statusMap.get(id)));
        }
        state.setPlayerStates(playerStates);
        return state;
    }

    /**
     * 리플렉션을 통해 private advanceTurn(RoundState) 를 직접 호출한다.
     */
    private void invokeAdvanceTurn(RoundState state) throws Exception {
        Method method = RoundService.class.getDeclaredMethod("advanceTurn", RoundState.class);
        method.setAccessible(true);
        method.invoke(roundService, state);
    }

    // ================================================================
    // 테스트 1: ELIMINATED 플레이어 건너뛰고 다음 ACTIVE 로 정상 전진
    // ================================================================

    @Nested
    @DisplayName("advanceTurn — ELIMINATED 플레이어 건너뛰기")
    class SkipEliminatedPlayers {

        private static final String PLAYER_A = "A";
        private static final String PLAYER_B = "B";
        private static final String PLAYER_C = "C";

        @Test
        @DisplayName("B가 ELIMINATED일 때 A가 버리면 다음 현재 플레이어는 C가 된다")
        void advanceTurn_skipsEliminatedPlayers_advancesToNextActive() {
            // A (index 0, ACTIVE) → B (ELIMINATED) → C (ACTIVE)
            // A가 버리기 후 advanceTurn → B 건너뜀 → C가 현재 플레이어가 돼야 한다
            Card cardToDiscard = new Card(Suit.SPADE, Rank.ACE);
            List<Card> handA = new ArrayList<>(List.of(
                    cardToDiscard,
                    new Card(Suit.HEART, Rank.TWO),
                    new Card(Suit.DIAMOND, Rank.THREE)
            ));
            List<Card> handB = makeCards(3);
            List<Card> handC = makeCards(3);

            List<String> turnOrder = List.of(PLAYER_A, PLAYER_B, PLAYER_C);
            Map<String, PlayerStatus> statusMap = new LinkedHashMap<>();
            statusMap.put(PLAYER_A, PlayerStatus.ACTIVE);
            statusMap.put(PLAYER_B, PlayerStatus.ELIMINATED);
            statusMap.put(PLAYER_C, PlayerStatus.ACTIVE);
            Map<String, List<Card>> handMap = new LinkedHashMap<>();
            handMap.put(PLAYER_A, handA);
            handMap.put(PLAYER_B, handB);
            handMap.put(PLAYER_C, handC);

            RoundState state = buildState(
                    turnOrder, statusMap, handMap,
                    0, TurnPhase.ACTION,
                    makeCards(5), new ArrayList<>()
            );

            // A가 카드를 버림 → advanceTurn 호출됨
            roundService.handleDiscard(state, PLAYER_A, new DiscardRequest(cardToDiscard));

            // endCondition 없음 (GOING_OUT 아님)
            assertThat(state.getEndCondition()).isNull();

            // 현재 플레이어가 C 여야 한다 (B를 건너뜀)
            String currentPlayer = state.getTurnOrder().get(state.getCurrentPlayerIndex());
            assertThat(currentPlayer).isEqualTo(PLAYER_C);
        }

        @Test
        @DisplayName("B, C 모두 ACTIVE일 때 A가 버리면 다음 현재 플레이어는 B가 된다 (정상 전진)")
        void advanceTurn_allActive_advancesToImmediateNext() {
            Card cardToDiscard = new Card(Suit.SPADE, Rank.ACE);
            List<Card> handA = new ArrayList<>(List.of(
                    cardToDiscard,
                    new Card(Suit.HEART, Rank.TWO)
            ));
            List<Card> handB = makeCards(3);
            List<Card> handC = makeCards(3);

            List<String> turnOrder = List.of(PLAYER_A, PLAYER_B, PLAYER_C);
            Map<String, PlayerStatus> statusMap = new LinkedHashMap<>();
            statusMap.put(PLAYER_A, PlayerStatus.ACTIVE);
            statusMap.put(PLAYER_B, PlayerStatus.ACTIVE);
            statusMap.put(PLAYER_C, PlayerStatus.ACTIVE);
            Map<String, List<Card>> handMap = new LinkedHashMap<>();
            handMap.put(PLAYER_A, handA);
            handMap.put(PLAYER_B, handB);
            handMap.put(PLAYER_C, handC);

            RoundState state = buildState(
                    turnOrder, statusMap, handMap,
                    0, TurnPhase.ACTION,
                    makeCards(5), new ArrayList<>()
            );

            roundService.handleDiscard(state, PLAYER_A, new DiscardRequest(cardToDiscard));

            assertThat(state.getEndCondition()).isNull();
            String currentPlayer = state.getTurnOrder().get(state.getCurrentPlayerIndex());
            assertThat(currentPlayer).isEqualTo(PLAYER_B);
        }
    }

    // ================================================================
    // 테스트 2: 모든 플레이어가 ELIMINATED → BANKRUPTCY 강제 설정, currentPlayerIndex 불변
    // ================================================================

    @Nested
    @DisplayName("advanceTurn — 모든 플레이어 ELIMINATED 시 BANKRUPTCY 강제 설정")
    class AllPlayersEliminated {

        private static final String PLAYER_A = "A";
        private static final String PLAYER_B = "B";

        @Test
        @DisplayName("모든 플레이어가 ELIMINATED이고 endCondition이 null이면 BANKRUPTCY가 설정된다")
        void advanceTurn_allPlayersEliminated_setsBankruptcyEndCondition() throws Exception {
            List<Card> handA = makeCards(2);
            List<Card> handB = makeCards(2);

            List<String> turnOrder = List.of(PLAYER_A, PLAYER_B);
            Map<String, PlayerStatus> statusMap = new LinkedHashMap<>();
            statusMap.put(PLAYER_A, PlayerStatus.ELIMINATED);
            statusMap.put(PLAYER_B, PlayerStatus.ELIMINATED);
            Map<String, List<Card>> handMap = new LinkedHashMap<>();
            handMap.put(PLAYER_A, handA);
            handMap.put(PLAYER_B, handB);

            RoundState state = buildState(
                    turnOrder, statusMap, handMap,
                    0, TurnPhase.ACTION,
                    makeCards(5), makeCards(1)
            );

            // 리플렉션으로 advanceTurn 직접 호출
            invokeAdvanceTurn(state);

            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.BANKRUPTCY);
        }

        @Test
        @DisplayName("모든 플레이어가 ELIMINATED이고 endCondition이 null이면 currentPlayerIndex는 변경되지 않는다")
        void advanceTurn_allPlayersEliminated_doesNotUpdateCurrentPlayerIndex() throws Exception {
            List<Card> handA = makeCards(2);
            List<Card> handB = makeCards(2);

            List<String> turnOrder = List.of(PLAYER_A, PLAYER_B);
            Map<String, PlayerStatus> statusMap = new LinkedHashMap<>();
            statusMap.put(PLAYER_A, PlayerStatus.ELIMINATED);
            statusMap.put(PLAYER_B, PlayerStatus.ELIMINATED);
            Map<String, List<Card>> handMap = new LinkedHashMap<>();
            handMap.put(PLAYER_A, handA);
            handMap.put(PLAYER_B, handB);

            RoundState state = buildState(
                    turnOrder, statusMap, handMap,
                    0, TurnPhase.ACTION,
                    makeCards(5), makeCards(1)
            );

            int indexBefore = state.getCurrentPlayerIndex();

            invokeAdvanceTurn(state);

            assertThat(state.getCurrentPlayerIndex()).isEqualTo(indexBefore);
        }
    }

    // ================================================================
    // 테스트 3: 모든 플레이어 ELIMINATED이고 endCondition이 이미 설정된 경우 → 덮어쓰지 않음
    // ================================================================

    @Nested
    @DisplayName("advanceTurn — endCondition 이미 설정된 경우 덮어쓰지 않음")
    class EndConditionAlreadySet {

        private static final String PLAYER_A = "A";
        private static final String PLAYER_B = "B";

        @Test
        @DisplayName("모든 플레이어가 ELIMINATED이고 endCondition이 GOING_OUT이면 BANKRUPTCY로 덮어쓰지 않는다")
        void advanceTurn_allPlayersEliminated_endConditionAlreadySet_doesNotOverwrite() throws Exception {
            List<Card> handA = makeCards(2);
            List<Card> handB = makeCards(2);

            List<String> turnOrder = List.of(PLAYER_A, PLAYER_B);
            Map<String, PlayerStatus> statusMap = new LinkedHashMap<>();
            statusMap.put(PLAYER_A, PlayerStatus.ELIMINATED);
            statusMap.put(PLAYER_B, PlayerStatus.ELIMINATED);
            Map<String, List<Card>> handMap = new LinkedHashMap<>();
            handMap.put(PLAYER_A, handA);
            handMap.put(PLAYER_B, handB);

            RoundState state = buildState(
                    turnOrder, statusMap, handMap,
                    0, TurnPhase.ACTION,
                    makeCards(5), makeCards(1)
            );

            // 이미 GOING_OUT 으로 설정된 상태
            state.setEndCondition(RoundEndCondition.GOING_OUT);

            invokeAdvanceTurn(state);

            // GOING_OUT 이 BANKRUPTCY 로 덮어쓰여서는 안 됨
            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.GOING_OUT);
        }

        @Test
        @DisplayName("모든 플레이어가 ELIMINATED이고 endCondition이 이미 설정된 경우 currentPlayerIndex는 변경되지 않는다")
        void advanceTurn_allPlayersEliminated_endConditionAlreadySet_doesNotUpdateIndex() throws Exception {
            List<Card> handA = makeCards(2);
            List<Card> handB = makeCards(2);

            List<String> turnOrder = List.of(PLAYER_A, PLAYER_B);
            Map<String, PlayerStatus> statusMap = new LinkedHashMap<>();
            statusMap.put(PLAYER_A, PlayerStatus.ELIMINATED);
            statusMap.put(PLAYER_B, PlayerStatus.ELIMINATED);
            Map<String, List<Card>> handMap = new LinkedHashMap<>();
            handMap.put(PLAYER_A, handA);
            handMap.put(PLAYER_B, handB);

            RoundState state = buildState(
                    turnOrder, statusMap, handMap,
                    0, TurnPhase.ACTION,
                    makeCards(5), makeCards(1)
            );

            state.setEndCondition(RoundEndCondition.GOING_OUT);
            int indexBefore = state.getCurrentPlayerIndex();

            invokeAdvanceTurn(state);

            assertThat(state.getCurrentPlayerIndex()).isEqualTo(indexBefore);
        }
    }
}
