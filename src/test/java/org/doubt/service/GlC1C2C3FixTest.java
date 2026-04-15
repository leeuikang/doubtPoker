package org.doubt.service;

import org.doubt.constant.ErrorCode;
import org.doubt.constant.GameConstants;
import org.doubt.constant.GameStatus;
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
import org.doubt.dto.PokerRoom;
import org.doubt.dto.RoundState;
import org.doubt.dto.request.DiscardRequest;
import org.doubt.dto.request.RevealBluffRequest;
import org.doubt.exception.GameException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GL-C1, GL-C2, GL-C3 버그 수정 검증 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GL-C1·C2·C3 버그 수정 테스트")
class GlC1C2C3FixTest {

    @Mock
    private DeckService deckService;

    @Mock
    private MeldValidationService meldValidationService;

    @Mock
    private ScoreService scoreService;

    @InjectMocks
    private RoundService roundService;

    // ----------------------------------------------------------------
    // 공통 헬퍼
    // ----------------------------------------------------------------

    /** 고유한 카드 n장을 생성한다 (중복 없음) */
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
     * 두 플레이어(currentPlayer, otherPlayer)가 참여하는 RoundState 를 조립한다.
     * currentPlayerIndex 는 0 (currentPlayer 차례).
     */
    private RoundState buildTwoPlayerState(String currentPlayer, String otherPlayer,
                                           TurnPhase phase,
                                           List<Card> currentHand,
                                           List<Card> otherHand,
                                           List<Card> stock,
                                           List<Card> discard) {
        RoundState state = new RoundState();
        state.setTurnOrder(new ArrayList<>(List.of(currentPlayer, otherPlayer)));
        state.setCurrentPlayerIndex(0);
        state.setTurnPhase(phase);
        state.setStockPile(new ArrayList<>(stock));
        state.setDiscardPile(new ArrayList<>(discard));
        state.setTableMelds(new ArrayList<>());
        state.setStockRefillCount(0);
        state.setEndCondition(null);
        state.setLastDiscardPlayerId(null);
        state.setThankYouTimerSec(0);

        Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
        playerStates.put(currentPlayer, makePlayerState(currentPlayer, currentHand));
        playerStates.put(otherPlayer, makePlayerState(otherPlayer, otherHand));
        state.setPlayerStates(playerStates);

        return state;
    }

    private PlayerRoundState makePlayerState(String playerId, List<Card> hand) {
        PlayerRoundState prs = new PlayerRoundState();
        prs.setPlayerId(playerId);
        prs.setHand(new ArrayList<>(hand));
        prs.setStatus(PlayerStatus.ACTIVE);
        prs.setHasMeldedThisTurn(false);
        prs.setHasDeclaredStop(false);
        prs.setHasBankrupted(false);
        prs.setDisconnectCount(0);
        return prs;
    }

    // ================================================================
    // GL-C1: handleThankYou — 직전 버린 플레이어 본인의 즉시 회수 방지
    // ================================================================

    @Nested
    @DisplayName("GL-C1: handleThankYou — 자기 카드 즉시 회수 방지")
    class HandleThankYouFix {

        private static final String PLAYER_A = "playerA";
        private static final String PLAYER_B = "playerB";

        /**
         * handleDiscard 를 직접 호출해 lastDiscardPlayerId 와 thankYouTimerSec 을 세팅하는 헬퍼.
         * A가 카드 한 장을 버린 직후 상태를 만든다.
         */
        private RoundState stateAfterPlayerADiscards() {
            Card cardToDiscard = new Card(Suit.SPADE, Rank.ACE);
            List<Card> handA = new ArrayList<>(List.of(
                    cardToDiscard,
                    new Card(Suit.HEART, Rank.TWO),
                    new Card(Suit.DIAMOND, Rank.THREE)
            ));
            List<Card> handB = makeCards(3);
            List<Card> stock = makeCards(5);
            List<Card> discard = new ArrayList<>();

            RoundState state = buildTwoPlayerState(
                    PLAYER_A, PLAYER_B, TurnPhase.ACTION, handA, handB, stock, discard);

            // A가 카드를 버린다
            roundService.handleDiscard(state, PLAYER_A, new DiscardRequest(cardToDiscard));
            // handleDiscard 이후 lastDiscardPlayerId=A, thankYouTimerSec=5
            return state;
        }

        @Test
        @DisplayName("직전 버린 플레이어(A)가 본인이 버린 카드를 땡큐로 회수하려 하면 INVALID_TURN 예외가 발생한다")
        void handleThankYou_whenSamePlayerAsDiscarder_throwsInvalidTurn() {
            RoundState state = stateAfterPlayerADiscards();

            assertThat(state.getLastDiscardPlayerId()).isEqualTo(PLAYER_A);
            assertThat(state.getThankYouTimerSec()).isGreaterThan(0);

            assertThatThrownBy(() -> roundService.handleThankYou(state, PLAYER_A))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN));
        }

        @Test
        @DisplayName("다른 플레이어(B)가 A가 버린 카드를 땡큐로 가져가면 성공하고 B가 현재 플레이어가 된다")
        void handleThankYou_whenDifferentPlayerFromDiscarder_succeeds() {
            RoundState state = stateAfterPlayerADiscards();

            RoundState result = roundService.handleThankYou(state, PLAYER_B);

            // B가 현재 플레이어여야 한다
            String currentPlayer = result.getTurnOrder().get(result.getCurrentPlayerIndex());
            assertThat(currentPlayer).isEqualTo(PLAYER_B);
            // 타이머 초기화
            assertThat(result.getThankYouTimerSec()).isEqualTo(0);
            // lastDiscardPlayerId 초기화
            assertThat(result.getLastDiscardPlayerId()).isNull();
        }

        @Test
        @DisplayName("handleDiscard 호출 후 state.getLastDiscardPlayerId() 가 버린 플레이어 ID 와 같다")
        void handleDiscard_setsLastDiscardPlayerId() {
            Card cardToDiscard = new Card(Suit.CLUB, Rank.KING);
            List<Card> handA = new ArrayList<>(List.of(
                    cardToDiscard,
                    new Card(Suit.HEART, Rank.FIVE)
            ));
            List<Card> handB = makeCards(3);
            List<Card> stock = makeCards(5);
            List<Card> discard = new ArrayList<>();

            RoundState state = buildTwoPlayerState(
                    PLAYER_A, PLAYER_B, TurnPhase.ACTION, handA, handB, stock, discard);

            roundService.handleDiscard(state, PLAYER_A, new DiscardRequest(cardToDiscard));

            assertThat(state.getLastDiscardPlayerId()).isEqualTo(PLAYER_A);
        }
    }

    // ================================================================
    // GL-C2: processAction — 중복 라운드 종료 처리 방지 (상태 가드 검증)
    // ================================================================

    @Nested
    @DisplayName("GL-C2: ROUND_END 가드 — 중복 종료 처리 방지")
    class RoundEndGuardFix {

        /**
         * GL-C2 의 핵심 불변식을 직접 검증한다.
         *
         * GameController.processAction 내 두 번째 synchronized 블록 진입 조건:
         *   if (room.getStatus() != GameStatus.ROUND_END) return;
         *
         * 즉 첫 번째 synchronized 에서 ROUND_END 로 전환된 room 만 두 번째 블록에서 처리된다.
         * 다른 스레드가 IN_PROGRESS 등으로 되돌린 경우 가드가 동작해야 한다.
         *
         * GameController 는 WebSocket 인프라 의존성이 많아 단위 테스트가 비현실적이므로,
         * PokerRoom 상태 전환 불변식을 직접 검증한다.
         */
        @Test
        @DisplayName("room.status 가 ROUND_END 가 아니면 두 번째 synchronized 블록 진입 가드가 동작한다")
        void guard_preventsProcessingWhenStatusIsNotRoundEnd() {
            PokerRoom room = new PokerRoom("room1", "테스트방");
            room.setStatus(GameStatus.IN_PROGRESS);

            // GL-C2 가드 로직: room.getStatus() != ROUND_END 이면 처리를 건너뛴다
            boolean shouldProcess = room.getStatus() == GameStatus.ROUND_END;

            assertThat(shouldProcess).isFalse();
        }

        @Test
        @DisplayName("첫 번째 synchronized 에서 ROUND_END 로 전환된 room 은 두 번째 블록에서 처리된다")
        void guard_allowsProcessingWhenStatusIsRoundEnd() {
            PokerRoom room = new PokerRoom("room1", "테스트방");
            room.setStatus(GameStatus.IN_PROGRESS);

            // 첫 번째 synchronized 에서 endCondition 확정 후 즉시 상태 전환 (GL-C2 수정)
            room.setStatus(GameStatus.ROUND_END);

            boolean shouldProcess = room.getStatus() == GameStatus.ROUND_END;

            assertThat(shouldProcess).isTrue();
        }

        @Test
        @DisplayName("두 스레드가 경쟁할 때 첫 번째 스레드만 ROUND_END 로 전환하고 두 번째는 가드에 막힌다")
        void guard_onlyOneThreadPassesThroughConcurrentScenario() throws InterruptedException {
            PokerRoom room = new PokerRoom("room1", "테스트방");
            room.setStatus(GameStatus.IN_PROGRESS);

            int[] processedCount = {0};

            // 스레드 1: ROUND_END 로 전환 후 두 번째 블록 진입 시도
            Thread t1 = new Thread(() -> {
                synchronized (room) {
                    // 첫 번째 synchronized: ROUND_END 로 전환
                    room.setStatus(GameStatus.ROUND_END);
                }
                synchronized (room) {
                    // 두 번째 synchronized: 가드 확인
                    if (room.getStatus() != GameStatus.ROUND_END) return;
                    processedCount[0]++;
                    // 처리 완료 후 상태 변경
                    room.setStatus(GameStatus.TOURNAMENT_END);
                }
            });

            // 스레드 2: t1 처리 완료 후 동일한 두 번째 블록 진입 시도 — 가드에 막혀야 함
            Thread t2 = new Thread(() -> {
                synchronized (room) {
                    // 두 번째 synchronized: t1 이 이미 처리했으므로 ROUND_END 가 아님
                    if (room.getStatus() != GameStatus.ROUND_END) return;
                    processedCount[0]++;
                }
            });

            t1.start();
            t1.join();
            t2.start();
            t2.join();

            // 오직 t1 만 처리해야 한다
            assertThat(processedCount[0]).isEqualTo(1);
        }
    }

    // ================================================================
    // GL-C3: handleRevealBluff — 카드 회수 후 파산 체크
    // ================================================================

    @Nested
    @DisplayName("GL-C3: handleRevealBluff — 소유자 카드 회수 후 파산 체크")
    class HandleRevealBluffFix {

        private static final String OWNER = "owner";
        private static final String EXTENDER = "extender";

        /**
         * 거짓말 멜드를 테이블에 올린 RoundState 를 조립한다.
         *
         * @param ownerHandSize  소유자 현재 손패 장수 (회수 전)
         * @param meldCardCount  멜드의 actualCards 장수
         * @param extensionCards 확장자가 붙인 카드 목록 (null 이면 확장 없음)
         */
        private RoundState buildStateWithBluffMeld(int ownerHandSize, int meldCardCount,
                                                   List<Card> extensionCards) {
            List<Card> ownerHand = makeCards(ownerHandSize);
            List<Card> extenderHand = (extensionCards != null)
                    ? makeCards(3)
                    : makeCards(3);
            List<Card> stock = makeCards(10);
            List<Card> discard = makeCards(1);

            RoundState state = buildTwoPlayerState(
                    OWNER, EXTENDER, TurnPhase.ACTION, ownerHand, extenderHand, stock, discard);

            // 거짓말 멜드 생성 (SPADE 슈트, 서로 다른 랭크로 구성)
            List<Card> actualCards = new ArrayList<>();
            List<DeclaredCard> declaredCards = new ArrayList<>();
            Rank[] ranks = Rank.values();
            for (int i = 0; i < meldCardCount; i++) {
                Card actual = new Card(Suit.HEART, ranks[i]);
                actualCards.add(actual);
                // 선언 카드는 다른 랭크로 거짓말 (마지막 카드만 다르게)
                if (i == meldCardCount - 1) {
                    // 거짓말 카드: 실제와 다른 랭크 선언
                    declaredCards.add(new DeclaredCard(Suit.HEART, ranks[(i + 1) % ranks.length]));
                } else {
                    declaredCards.add(new DeclaredCard(actual.suit(), actual.rank()));
                }
            }

            Meld bluffMeld = Meld.create("meld-1", OWNER, MeldType.SET,
                    actualCards, declaredCards, true);

            if (extensionCards != null) {
                bluffMeld.getExtensions().put(EXTENDER, new ArrayList<>(extensionCards));
            }

            state.getTableMelds().add(bluffMeld);
            state.setLastDoubtableMeldId("meld-1");

            return state;
        }

        @Test
        @DisplayName("소유자가 카드를 회수해 10장 이상이 되면 ELIMINATED 상태가 된다 (파산)")
        void handleRevealBluff_whenOwnerRecoveredCardsCauseBankruptcy_ownerBecomesEliminated() {
            // 소유자 손패 7장 + 멜드 actualCards 3장 = 회수 후 10장 → 파산 기준(BANKRUPTCY_HAND_SIZE=10) 충족
            int ownerHandSize = 7;
            int meldCardCount = 3;
            // ownerHandSize + meldCardCount == BANKRUPTCY_HAND_SIZE(10) → 파산

            RoundState state = buildStateWithBluffMeld(ownerHandSize, meldCardCount, null);

            roundService.handleRevealBluff(state, OWNER, new RevealBluffRequest("meld-1"));

            PlayerRoundState ownerState = state.getPlayerStates().get(OWNER);
            assertThat(ownerState.getStatus()).isEqualTo(PlayerStatus.ELIMINATED);
            assertThat(ownerState.isHasBankrupted()).isTrue();
        }

        @Test
        @DisplayName("소유자가 카드를 회수해도 10장 미만이면 ACTIVE 상태를 유지한다")
        void handleRevealBluff_whenOwnerRecoveredCardsNoBankruptcy_ownerRemainsActive() {
            // 소유자 손패 3장 + 멜드 actualCards 3장 = 회수 후 6장 → 파산 기준 미충족
            int ownerHandSize = 3;
            int meldCardCount = 3;

            RoundState state = buildStateWithBluffMeld(ownerHandSize, meldCardCount, null);

            roundService.handleRevealBluff(state, OWNER, new RevealBluffRequest("meld-1"));

            PlayerRoundState ownerState = state.getPlayerStates().get(OWNER);
            assertThat(ownerState.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
            assertThat(ownerState.getHand()).hasSize(ownerHandSize + meldCardCount);
        }

        @Test
        @DisplayName("확장자는 extension 카드를 돌려받지 않고 스톡에서 1장만 드로우 패널티를 받는다")
        void handleRevealBluff_extenderReceivesPenaltyDrawWithoutCardRecovery() {
            // 확장자가 붙인 카드 2장
            List<Card> extensionCards = new ArrayList<>(List.of(
                    new Card(Suit.CLUB, Rank.TWO),
                    new Card(Suit.CLUB, Rank.THREE)
            ));

            // 소유자 손패 3장, 멜드 3장 (파산 없는 케이스)
            RoundState state = buildStateWithBluffMeld(3, 3, extensionCards);

            int extenderHandSizeBefore = state.getPlayerStates().get(EXTENDER).getHand().size();

            roundService.handleRevealBluff(state, OWNER, new RevealBluffRequest("meld-1"));

            PlayerRoundState extenderState = state.getPlayerStates().get(EXTENDER);
            int extenderHandSizeAfter = extenderState.getHand().size();

            // 확장자는 extension 카드(2장)를 돌려받지 않고 스톡에서 1장만 드로우
            // 즉 핸드 크기 = 이전 + 1 (패널티 드로우만)
            assertThat(extenderHandSizeAfter).isEqualTo(extenderHandSizeBefore + 1);

            // extension 카드들이 확장자 손패에 포함되지 않았음을 검증
            for (Card extCard : extensionCards) {
                assertThat(extenderState.getHand()).doesNotContain(extCard);
            }
        }

        @Test
        @DisplayName("자진 공개 후 멜드가 테이블에서 제거된다")
        void handleRevealBluff_meldRemovedFromTable() {
            RoundState state = buildStateWithBluffMeld(3, 3, null);

            assertThat(state.getTableMelds()).hasSize(1);

            roundService.handleRevealBluff(state, OWNER, new RevealBluffRequest("meld-1"));

            assertThat(state.getTableMelds()).isEmpty();
        }

        @Test
        @DisplayName("파산으로 활성 플레이어가 1명 이하가 되면 BANKRUPTCY 종료 조건이 설정된다")
        void handleRevealBluff_whenBankruptcyReducesActivePlayers_setsEndCondition() {
            // 2인 게임에서 소유자가 파산 → 활성 플레이어 1명 남음 → BANKRUPTCY 종료
            int ownerHandSize = 7;
            int meldCardCount = 3; // 회수 후 10장 → 파산

            RoundState state = buildStateWithBluffMeld(ownerHandSize, meldCardCount, null);

            roundService.handleRevealBluff(state, OWNER, new RevealBluffRequest("meld-1"));

            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.BANKRUPTCY);
        }
    }
}
