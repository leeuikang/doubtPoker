package org.doubt.service;

import org.doubt.constant.DrawSource;
import org.doubt.constant.ErrorCode;
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
import org.doubt.dto.request.DrawRequest;
import org.doubt.dto.request.ExtendRequest;
import org.doubt.dto.request.DiscardRequest;
import org.doubt.constant.GameConstants;
import org.doubt.dto.request.DoubtRequest;
import org.doubt.dto.request.MeldRequest;
import org.doubt.dto.request.RevealBluffRequest;
import org.doubt.dto.request.StopRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoundService")
class RoundServiceTest {

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

    /** 지정한 장수의 카드 리스트를 생성 (SPADE ACE 고정) */
    private List<Card> makeCards(int count) {
        List<Card> cards = new ArrayList<>();
        Rank[] ranks = Rank.values();
        Suit[] suits = Suit.values();
        int idx = 0;
        for (int i = 0; i < count; i++) {
            cards.add(new Card(suits[idx % suits.length], ranks[idx % ranks.length]));
            idx++;
        }
        return cards;
    }

    /** 기본 RoundState 를 직접 조립하는 헬퍼 */
    private RoundState buildState(String currentPlayerId, TurnPhase phase,
                                  List<Card> stock, List<Card> discard,
                                  List<Card> currentPlayerHand) {
        RoundState state = new RoundState();
        state.setTurnOrder(new ArrayList<>(List.of(currentPlayerId)));
        state.setCurrentPlayerIndex(0);
        state.setTurnPhase(phase);
        state.setStockPile(stock);
        state.setDiscardPile(discard);
        state.setStockRefillCount(0);
        state.setEndCondition(null);
        state.setTableMelds(new ArrayList<>());

        PlayerRoundState prs = new PlayerRoundState();
        prs.setPlayerId(currentPlayerId);
        prs.setHand(new ArrayList<>(currentPlayerHand));
        prs.setStatus(PlayerStatus.ACTIVE);
        prs.setHasMeldedThisTurn(false);
        prs.setHasDeclaredStop(false);
        prs.setHasBankrupted(false);
        prs.setDisconnectCount(0);

        Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
        playerStates.put(currentPlayerId, prs);
        state.setPlayerStates(playerStates);

        return state;
    }

    /**
     * actualCards 와 1:1 대응하는 DeclaredCard 리스트를 생성한다 (정직 선언).
     * 거짓말 테스트 시에는 별도로 조립한다.
     */
    private List<DeclaredCard> toHonestDeclared(List<Card> cards) {
        List<DeclaredCard> declared = new ArrayList<>();
        for (Card c : cards) {
            declared.add(new DeclaredCard(c.suit(), c.rank()));
        }
        return declared;
    }

    // ----------------------------------------------------------------
    // startRound 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("startRound")
    class StartRound {

        private List<String> twoPlayers;
        private List<String> threePlayers;

        @BeforeEach
        void setUp() {
            twoPlayers = List.of("p1", "p2");
            threePlayers = List.of("p1", "p2", "p3");
        }

        /** 스텁: 플레이어별 7장 + STOCK 나머지를 반환 */
        private void stubDeckService(List<String> playerIds) {
            List<Card> fakeDeck = makeCards(52);
            when(deckService.createShuffledDeck()).thenReturn(fakeDeck);

            Map<String, List<Card>> fakeDealt = new LinkedHashMap<>();
            int offset = 0;
            for (String pid : playerIds) {
                fakeDealt.put(pid, new ArrayList<>(fakeDeck.subList(offset, offset + 7)));
                offset += 7;
            }
            fakeDealt.put("STOCK", new ArrayList<>(fakeDeck.subList(offset, fakeDeck.size())));
            when(deckService.deal(any(), any(), anyInt())).thenReturn(fakeDealt);
        }

        @Test
        @DisplayName("플레이어 1명이면 NOT_ENOUGH_PLAYERS 예외가 발생한다")
        void throws_when_player_count_is_1() {
            assertThatThrownBy(() -> roundService.startRound("room1", List.of("p1"), "p1"))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_ENOUGH_PLAYERS));
        }

        @Test
        @DisplayName("플레이어 6명이면 NOT_ENOUGH_PLAYERS 예외가 발생한다")
        void throws_when_player_count_is_6() {
            List<String> sixPlayers = List.of("p1", "p2", "p3", "p4", "p5", "p6");
            assertThatThrownBy(() -> roundService.startRound("room1", sixPlayers, "p1"))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_ENOUGH_PLAYERS));
        }

        @Test
        @DisplayName("플레이어 목록에 없는 firstPlayerId 이면 INVALID_TURN 예외가 발생한다")
        void throws_when_first_player_not_in_list() {
            stubDeckService(twoPlayers);
            assertThatThrownBy(() -> roundService.startRound("room1", twoPlayers, "unknown"))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN));
        }

        @Test
        @DisplayName("2명 최소 인원으로 정상적으로 라운드가 시작된다")
        void success_with_two_players() {
            stubDeckService(twoPlayers);
            RoundState state = roundService.startRound("room1", twoPlayers, "p1");

            assertThat(state).isNotNull();
            assertThat(state.getPlayerStates()).hasSize(2);
        }

        @Test
        @DisplayName("5명 최대 인원으로 정상적으로 라운드가 시작된다")
        void success_with_five_players() {
            List<String> fivePlayers = List.of("p1", "p2", "p3", "p4", "p5");
            stubDeckService(fivePlayers);
            RoundState state = roundService.startRound("room1", fivePlayers, "p1");

            assertThat(state.getPlayerStates()).hasSize(5);
        }

        @Test
        @DisplayName("각 플레이어 손패는 정확히 7장이다")
        void each_player_has_7_cards() {
            stubDeckService(threePlayers);
            RoundState state = roundService.startRound("room1", threePlayers, "p1");

            for (String pid : threePlayers) {
                assertThat(state.getPlayerStates().get(pid).getHand()).hasSize(7);
            }
        }

        @Test
        @DisplayName("버림더미에 초기 카드 1장이 세팅된다")
        void discard_pile_has_one_card_after_start() {
            stubDeckService(twoPlayers);
            RoundState state = roundService.startRound("room1", twoPlayers, "p1");

            assertThat(state.getDiscardPile()).hasSize(1);
        }

        @Test
        @DisplayName("스톡 파일은 버림더미 1장을 뺀 나머지 카드로 구성된다")
        void stock_pile_size_is_deck_minus_dealt_minus_one() {
            stubDeckService(twoPlayers);
            RoundState state = roundService.startRound("room1", twoPlayers, "p1");

            // 52 - (7 * 2명) - 버림더미 1장 = 37장
            assertThat(state.getStockPile()).hasSize(37);
        }

        @Test
        @DisplayName("firstPlayerId 가 turnOrder[0] 에 위치한다")
        void first_player_is_at_index_0_of_turn_order() {
            stubDeckService(threePlayers);
            RoundState state = roundService.startRound("room1", threePlayers, "p2");

            assertThat(state.getTurnOrder().get(0)).isEqualTo("p2");
            assertThat(state.getCurrentPlayerIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("turnOrder 는 firstPlayerId 부터 원래 순서를 유지하며 로테이션된다")
        void turn_order_rotates_clockwise_from_first_player() {
            stubDeckService(threePlayers);
            // threePlayers = [p1, p2, p3], first = p2
            RoundState state = roundService.startRound("room1", threePlayers, "p2");

            assertThat(state.getTurnOrder()).containsExactly("p2", "p3", "p1");
        }

        @Test
        @DisplayName("RoundState 초기값이 규칙에 맞게 설정된다")
        void round_state_initial_values_are_correct() {
            stubDeckService(twoPlayers);
            RoundState state = roundService.startRound("room1", twoPlayers, "p1");

            assertThat(state.getTurnPhase()).isEqualTo(TurnPhase.DRAW);
            assertThat(state.getStockRefillCount()).isEqualTo(0);
            assertThat(state.getThankYouTimerSec()).isEqualTo(10);
            assertThat(state.getEndCondition()).isNull();
            assertThat(state.getTableMelds()).isEmpty();
        }

        @Test
        @DisplayName("각 PlayerRoundState 의 초기 플래그가 모두 false 이고 상태가 ACTIVE 이다")
        void player_round_state_initial_flags_are_false_and_status_is_active() {
            stubDeckService(twoPlayers);
            RoundState state = roundService.startRound("room1", twoPlayers, "p1");

            for (String pid : twoPlayers) {
                PlayerRoundState prs = state.getPlayerStates().get(pid);
                assertThat(prs.isHasMeldedThisTurn()).isFalse();
                assertThat(prs.isHasDeclaredStop()).isFalse();
                assertThat(prs.isHasBankrupted()).isFalse();
                assertThat(prs.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
                assertThat(prs.getDisconnectCount()).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("firstPlayerId 가 playerIds 의 첫 번째 원소인 경우에도 정상 동작한다")
        void success_when_first_player_is_at_index_0() {
            stubDeckService(threePlayers);
            RoundState state = roundService.startRound("room1", threePlayers, "p1");

            assertThat(state.getTurnOrder()).containsExactly("p1", "p2", "p3");
        }

        @Test
        @DisplayName("firstPlayerId 가 playerIds 의 마지막 원소인 경우 로테이션이 올바르다")
        void success_when_first_player_is_last_in_list() {
            stubDeckService(threePlayers);
            RoundState state = roundService.startRound("room1", threePlayers, "p3");

            assertThat(state.getTurnOrder()).containsExactly("p3", "p1", "p2");
        }
    }

    // ----------------------------------------------------------------
    // handleDraw 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleDraw")
    class HandleDraw {

        private static final String PLAYER_ID = "p1";

        @Nested
        @DisplayName("턴/페이즈 검증")
        class Validation {

            @Test
            @DisplayName("현재 플레이어가 아닌 플레이어가 드로우하면 INVALID_TURN 예외가 발생한다")
            void throws_invalid_turn_when_not_current_player() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), makeCards(3), makeCards(6));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                assertThatThrownBy(() -> roundService.handleDraw(state, "other", request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TURN));
            }

            @Test
            @DisplayName("DRAW 페이즈가 아닐 때 드로우하면 INVALID_TURN_PHASE 예외가 발생한다")
            void throws_invalid_turn_phase_when_not_draw_phase() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), makeCards(6));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                assertThatThrownBy(() -> roundService.handleDraw(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
            }

        }

        @Nested
        @DisplayName("StockDraw - 스톡에서 드로우")
        class StockDraw {

            @Test
            @DisplayName("스톡에서 드로우하면 손패가 1장 증가하고 turnPhase 가 ACTION 이 된다")
            void draw_from_stock_increases_hand_and_sets_action_phase() {
                List<Card> stock = makeCards(5);
                int stockSizeBefore = stock.size();
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        stock, makeCards(3), makeCards(6));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                assertThat(result.getPlayerStates().get(PLAYER_ID).getHand()).hasSize(7);
                assertThat(result.getStockPile()).hasSize(stockSizeBefore - 1);
                assertThat(result.getTurnPhase()).isEqualTo(TurnPhase.ACTION);
            }

            @Test
            @DisplayName("스톡에서 드로우하면 스톡 맨 앞(index 0) 카드가 드로우된다")
            void draw_from_stock_takes_first_card() {
                Card expectedCard = new Card(Suit.SPADE, Rank.ACE);
                List<Card> stock = new ArrayList<>();
                stock.add(expectedCard);
                stock.add(new Card(Suit.HEART, Rank.KING));

                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        stock, makeCards(1), makeCards(6));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                roundService.handleDraw(state, PLAYER_ID, request);

                List<Card> hand = state.getPlayerStates().get(PLAYER_ID).getHand();
                assertThat(hand).contains(expectedCard);
                assertThat(state.getStockPile()).doesNotContain(expectedCard);
            }

            @Test
            @DisplayName("endCondition 이 null 인 채로 반환된다 (파산 아닌 경우)")
            void end_condition_remains_null_for_normal_stock_draw() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), makeCards(3), makeCards(6));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                assertThat(result.getEndCondition()).isNull();
            }
        }

        @Nested
        @DisplayName("DiscardDraw - 버림더미에서 드로우")
        class DiscardDraw {

            @Test
            @DisplayName("버림더미에서 드로우하면 손패가 1장 증가하고 turnPhase 가 ACTION 이 된다")
            void draw_from_discard_increases_hand_and_sets_action_phase() {
                List<Card> discard = makeCards(3);
                int discardSizeBefore = discard.size();
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), discard, makeCards(6));

                DrawRequest request = new DrawRequest(DrawSource.DISCARD);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                assertThat(result.getPlayerStates().get(PLAYER_ID).getHand()).hasSize(7);
                assertThat(result.getDiscardPile()).hasSize(discardSizeBefore - 1);
                assertThat(result.getTurnPhase()).isEqualTo(TurnPhase.ACTION);
            }

            @Test
            @DisplayName("버림더미에서 드로우하면 버림더미의 마지막 카드(상단)가 드로우된다")
            void draw_from_discard_takes_last_card() {
                Card topCard = new Card(Suit.DIAMOND, Rank.QUEEN);
                List<Card> discard = new ArrayList<>();
                discard.add(new Card(Suit.CLUB, Rank.TWO));
                discard.add(topCard);  // 마지막 = 상단

                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), discard, makeCards(6));

                DrawRequest request = new DrawRequest(DrawSource.DISCARD);
                roundService.handleDraw(state, PLAYER_ID, request);

                List<Card> hand = state.getPlayerStates().get(PLAYER_ID).getHand();
                assertThat(hand).contains(topCard);
                assertThat(state.getDiscardPile()).doesNotContain(topCard);
            }

            @Test
            @DisplayName("버림더미가 비어있으면 INVALID_CARD 예외가 발생한다")
            void throws_invalid_card_when_discard_pile_is_empty() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), new ArrayList<>(), makeCards(6));

                DrawRequest request = new DrawRequest(DrawSource.DISCARD);
                assertThatThrownBy(() -> roundService.handleDraw(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CARD));
            }
        }

        @Nested
        @DisplayName("StockRefill - 스톡 재구성")
        class StockRefill {

            @Test
            @DisplayName("스톡이 비어있을 때 드로우하면 버림더미로 스톡이 재구성된다 (1회차)")
            void refill_stock_from_discard_on_first_depletion() {
                // 버림더미 5장, 스톡 비어있음
                List<Card> discard = new ArrayList<>(makeCards(5));
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        new ArrayList<>(), discard, makeCards(6));
                state.setStockRefillCount(0);

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                // 재구성 후 1장 드로우했으므로 endCondition null, refillCount 증가
                assertThat(result.getEndCondition()).isNull();
                assertThat(result.getStockRefillCount()).isEqualTo(1);
                // 버림더미는 상단 1장만 남아야 함
                assertThat(result.getDiscardPile()).hasSize(1);
                // 손패는 1장 늘어남
                assertThat(result.getPlayerStates().get(PLAYER_ID).getHand()).hasSize(7);
            }

            @Test
            @DisplayName("스톡이 비어있을 때 드로우하면 버림더미로 스톡이 재구성된다 (2회차)")
            void refill_stock_from_discard_on_second_depletion() {
                List<Card> discard = new ArrayList<>(makeCards(5));
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        new ArrayList<>(), discard, makeCards(6));
                state.setStockRefillCount(1);  // 이미 1회 재구성 이력

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                assertThat(result.getEndCondition()).isNull();
                assertThat(result.getStockRefillCount()).isEqualTo(2);
            }

            @Test
            @DisplayName("스톡 3번째 소진 시 endCondition 이 STOCK_DEPLETED 이 되고 드로우 없이 즉시 반환된다")
            void third_stock_depletion_sets_stock_depleted_end_condition() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        new ArrayList<>(), makeCards(3), makeCards(6));
                state.setStockRefillCount(2);  // 이미 2회 재구성 → 다음은 3번째

                int handSizeBefore = state.getPlayerStates().get(PLAYER_ID).getHand().size();
                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.STOCK_DEPLETED);
                // 드로우 없이 반환되므로 손패 크기 변화 없음
                assertThat(result.getPlayerStates().get(PLAYER_ID).getHand()).hasSize(handSizeBefore);
            }

            @Test
            @DisplayName("재구성 후 버림더미 상단 1장은 버림더미에 유지된다")
            void refill_keeps_top_discard_card_in_discard_pile() {
                Card topCard = new Card(Suit.HEART, Rank.ACE);
                List<Card> discard = new ArrayList<>();
                discard.add(new Card(Suit.SPADE, Rank.TWO));
                discard.add(new Card(Suit.CLUB, Rank.THREE));
                discard.add(topCard);  // 상단

                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        new ArrayList<>(), discard, makeCards(6));
                state.setStockRefillCount(0);

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                roundService.handleDraw(state, PLAYER_ID, request);

                // 버림더미에 상단 카드만 남아있어야 함
                assertThat(state.getDiscardPile()).containsExactly(topCard);
            }
        }

        @Nested
        @DisplayName("Bankruptcy - 파산 처리")
        class Bankruptcy {

            @Test
            @DisplayName("드로우 후 손패가 정확히 10장이면 파산 처리된다")
            void bankruptcy_when_hand_reaches_10_cards() {
                // 드로우 전 9장 → 드로우 후 10장
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), makeCards(3), makeCards(9));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                PlayerRoundState prs = result.getPlayerStates().get(PLAYER_ID);
                assertThat(prs.getHand()).hasSize(10);
                assertThat(prs.isHasBankrupted()).isTrue();
                assertThat(prs.getStatus()).isEqualTo(PlayerStatus.ELIMINATED);
                // 파산 후 1인 게임이므로 ACTIVE 플레이어가 0명 → BANKRUPTCY 종료 조건 설정
                assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.BANKRUPTCY);
            }

            @Test
            @DisplayName("드로우 후 손패가 10장 초과해도 파산 처리된다")
            void bankruptcy_when_hand_exceeds_10_cards() {
                // 드로우 전 10장 → 드로우 후 11장
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), makeCards(3), makeCards(10));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                PlayerRoundState prs = result.getPlayerStates().get(PLAYER_ID);
                assertThat(prs.isHasBankrupted()).isTrue();
                assertThat(prs.getStatus()).isEqualTo(PlayerStatus.ELIMINATED);
                assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.BANKRUPTCY);
            }

            @Test
            @DisplayName("드로우 후 손패가 9장이면 파산 처리되지 않는다")
            void no_bankruptcy_when_hand_is_9_cards() {
                // 드로우 전 8장 → 드로우 후 9장
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), makeCards(3), makeCards(8));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                PlayerRoundState prs = result.getPlayerStates().get(PLAYER_ID);
                assertThat(prs.isHasBankrupted()).isFalse();
                assertThat(prs.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
                assertThat(result.getEndCondition()).isNull();
            }

            @Test
            @DisplayName("파산 시 turnPhase 는 ACTION 으로 설정된다")
            void turn_phase_is_action_even_when_bankrupt() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), makeCards(3), makeCards(9));

                DrawRequest request = new DrawRequest(DrawSource.STOCK);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                assertThat(result.getTurnPhase()).isEqualTo(TurnPhase.ACTION);
            }

            @Test
            @DisplayName("버림더미 드로우로도 파산이 발생한다")
            void bankruptcy_triggered_by_discard_draw() {
                // 드로우 전 9장 → 드로우 후 10장
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), makeCards(3), makeCards(9));

                DrawRequest request = new DrawRequest(DrawSource.DISCARD);
                RoundState result = roundService.handleDraw(state, PLAYER_ID, request);

                PlayerRoundState prs = result.getPlayerStates().get(PLAYER_ID);
                assertThat(prs.isHasBankrupted()).isTrue();
                assertThat(prs.getStatus()).isEqualTo(PlayerStatus.ELIMINATED);
                assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.BANKRUPTCY);
            }
        }
    }

    // ----------------------------------------------------------------
    // handleMeld 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleMeld")
    class HandleMeld {

        private static final String PLAYER_ID = "p1";

        @Nested
        @DisplayName("검증")
        class Validation {

            @Test
            @DisplayName("현재 플레이어가 아닌 플레이어가 멜드하면 INVALID_TURN 예외가 발생한다")
            void throws_invalid_turn_when_not_current_player() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), makeCards(5));

                List<Card> cards = List.of(new Card(Suit.SPADE, Rank.ACE));
                MeldRequest request = new MeldRequest(
                        cards, toHonestDeclared(cards), MeldType.SOLO_SEVEN);

                assertThatThrownBy(() -> roundService.handleMeld(state, "other", request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TURN));
            }

            @Test
            @DisplayName("ACTION 페이즈가 아닌 DRAW 페이즈에서 멜드하면 INVALID_TURN_PHASE 예외가 발생한다")
            void throws_invalid_turn_phase_when_draw_phase() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                        makeCards(5), makeCards(3), makeCards(5));

                List<Card> cards = List.of(new Card(Suit.SPADE, Rank.ACE));
                MeldRequest request = new MeldRequest(
                        cards, toHonestDeclared(cards), MeldType.SOLO_SEVEN);

                assertThatThrownBy(() -> roundService.handleMeld(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
            }

            @Test
            @DisplayName("손패에 없는 카드를 멜드에 사용하면 INVALID_CARD 예외가 발생한다")
            void throws_invalid_card_when_card_not_in_hand() {
                // 손패에는 SPADE ACE 만 있음
                List<Card> hand = List.of(new Card(Suit.SPADE, Rank.ACE));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                // 손패에 없는 카드(HEART TWO)를 멜드에 사용
                List<Card> meldCards = List.of(new Card(Suit.HEART, Rank.TWO));
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                assertThatThrownBy(() -> roundService.handleMeld(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CARD));
            }

            @Test
            @DisplayName("validateMeld 가 false 를 반환하면 INVALID_MELD 예외가 발생한다")
            void throws_invalid_meld_when_validation_fails() {
                Card card = new Card(Suit.SPADE, Rank.SEVEN);
                List<Card> hand = new ArrayList<>(List.of(card));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                List<Card> meldCards = List.of(card);
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(false);

                assertThatThrownBy(() -> roundService.handleMeld(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_MELD));
            }
        }

        @Nested
        @DisplayName("거짓말 멜드")
        class BluffMeld {

            @Test
            @DisplayName("거짓말 멜드 후 손패가 비어있으면 CANNOT_GOING_OUT 예외가 발생한다")
            void throws_cannot_going_out_when_bluff_empties_hand() {
                // 손패에 카드 1장만 있고, 그 카드로 거짓말 멜드 → 손패 0장이 됨
                Card actual = new Card(Suit.SPADE, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(actual));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                // 실제는 TWO 인데 SEVEN 으로 선언 → 거짓말
                List<Card> meldCards = List.of(actual);
                List<DeclaredCard> declared = List.of(new DeclaredCard(Suit.SPADE, Rank.SEVEN));
                MeldRequest request = new MeldRequest(meldCards, declared, MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);
                when(meldValidationService.isBluffMeld(any(), any())).thenReturn(true); // CQ-W4

                assertThatThrownBy(() -> roundService.handleMeld(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.CANNOT_GOING_OUT));
            }

            @Test
            @DisplayName("거짓말 멜드 후 손패가 남아있으면 정상 처리된다")
            void bluff_meld_succeeds_when_hand_not_empty_after() {
                Card actual = new Card(Suit.SPADE, Rank.TWO);
                Card remaining = new Card(Suit.HEART, Rank.THREE);
                List<Card> hand = new ArrayList<>(List.of(actual, remaining));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                // 실제는 TWO 인데 SEVEN 으로 선언 → 거짓말
                List<Card> meldCards = List.of(actual);
                List<DeclaredCard> declared = List.of(new DeclaredCard(Suit.SPADE, Rank.SEVEN));
                MeldRequest request = new MeldRequest(meldCards, declared, MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);
                when(meldValidationService.isBluffMeld(any(), any())).thenReturn(true); // CQ-W4

                RoundState result = roundService.handleMeld(state, PLAYER_ID, request);

                // 멜드가 테이블에 추가되고 isBluff = true
                assertThat(result.getTableMelds()).hasSize(1);
                assertThat(result.getTableMelds().get(0).isBluff()).isTrue();
                // 손패에 remaining 만 남아있음
                assertThat(result.getPlayerStates().get(PLAYER_ID).getHand()).containsExactly(remaining);
                // 거짓말 멜드 → 손패 비어도 GOING_OUT 아님, 여기서는 손패가 남아있으므로 null
                assertThat(result.getEndCondition()).isNull();
            }

            @Test
            @DisplayName("정직한 멜드는 isBluff 가 false 이다")
            void honest_meld_has_bluff_false() {
                Card card = new Card(Suit.SPADE, Rank.SEVEN);
                List<Card> hand = new ArrayList<>(List.of(card, new Card(Suit.HEART, Rank.TWO)));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                List<Card> meldCards = List.of(card);
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleMeld(state, PLAYER_ID, request);

                assertThat(result.getTableMelds().get(0).isBluff()).isFalse();
            }
        }

        @Nested
        @DisplayName("고잉아웃")
        class GoingOut {

            @Test
            @DisplayName("정직한 멜드로 손패가 비어지면 GOING_OUT 종료 조건이 설정된다")
            void going_out_when_hand_empty_after_honest_meld() {
                Card card = new Card(Suit.SPADE, Rank.SEVEN);
                List<Card> hand = new ArrayList<>(List.of(card));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                List<Card> meldCards = List.of(card);
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleMeld(state, PLAYER_ID, request);

                assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.GOING_OUT);
                assertThat(result.getPlayerStates().get(PLAYER_ID).getHand()).isEmpty();
            }

            @Test
            @DisplayName("멜드 후 손패가 남아있으면 endCondition 이 null 이다")
            void no_going_out_when_hand_not_empty() {
                Card card = new Card(Suit.SPADE, Rank.SEVEN);
                Card extra = new Card(Suit.HEART, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(card, extra));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                List<Card> meldCards = List.of(card);
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleMeld(state, PLAYER_ID, request);

                assertThat(result.getEndCondition()).isNull();
            }
        }

        @Nested
        @DisplayName("정상 처리")
        class HappyPath {

            @Test
            @DisplayName("멜드 성공 시 테이블에 멜드가 추가된다")
            void meld_is_added_to_table_melds() {
                Card card = new Card(Suit.SPADE, Rank.SEVEN);
                List<Card> hand = new ArrayList<>(List.of(card, new Card(Suit.HEART, Rank.TWO)));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                List<Card> meldCards = List.of(card);
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleMeld(state, PLAYER_ID, request);

                assertThat(result.getTableMelds()).hasSize(1);
                Meld meld = result.getTableMelds().get(0);
                assertThat(meld.getOwnerId()).isEqualTo(PLAYER_ID);
                assertThat(meld.getType()).isEqualTo(MeldType.SOLO_SEVEN);
                assertThat(meld.getActualCards()).containsExactlyElementsOf(meldCards);
            }

            @Test
            @DisplayName("멜드 성공 시 lastDoubtableMeldId 가 해당 멜드 ID 로 설정된다")
            void last_doubtable_meld_id_is_set() {
                Card card = new Card(Suit.SPADE, Rank.SEVEN);
                List<Card> hand = new ArrayList<>(List.of(card, new Card(Suit.HEART, Rank.TWO)));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                List<Card> meldCards = List.of(card);
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleMeld(state, PLAYER_ID, request);

                assertThat(result.getLastDoubtableMeldId())
                        .isEqualTo(result.getTableMelds().get(0).getId());
            }

            @Test
            @DisplayName("멜드 성공 시 hasMeldedThisTurn 이 true 로 설정된다")
            void player_flags_set_after_meld() {
                Card card = new Card(Suit.SPADE, Rank.SEVEN);
                List<Card> hand = new ArrayList<>(List.of(card, new Card(Suit.HEART, Rank.TWO)));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                List<Card> meldCards = List.of(card);
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleMeld(state, PLAYER_ID, request);

                PlayerRoundState prs = result.getPlayerStates().get(PLAYER_ID);
                assertThat(prs.isHasMeldedThisTurn()).isTrue();
            }

            @Test
            @DisplayName("멜드 성공 시 actualCards 가 손패에서 제거된다")
            void actual_cards_removed_from_hand_after_meld() {
                Card meldCard = new Card(Suit.SPADE, Rank.SEVEN);
                Card keepCard = new Card(Suit.HEART, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(meldCard, keepCard));
                RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                        makeCards(5), makeCards(3), hand);

                List<Card> meldCards = List.of(meldCard);
                MeldRequest request = new MeldRequest(
                        meldCards, toHonestDeclared(meldCards), MeldType.SOLO_SEVEN);

                when(meldValidationService.validateMeld(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleMeld(state, PLAYER_ID, request);

                List<Card> handAfter = result.getPlayerStates().get(PLAYER_ID).getHand();
                assertThat(handAfter).doesNotContain(meldCard);
                assertThat(handAfter).contains(keepCard);
            }
        }
    }

    // ----------------------------------------------------------------
    // handleExtend 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleExtend")
    class HandleExtend {

        private static final String PLAYER_ID = "p1";

        /** 테이블 위에 멜드가 1개 있는 상태를 만드는 헬퍼 */
        private RoundState buildStateWithMeld(List<Card> playerHand, String meldId) {
            RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                    makeCards(5), makeCards(3), playerHand);

            // hasMeldedThisTurn = true (extend 전제 조건)
            state.getPlayerStates().get(PLAYER_ID).setHasMeldedThisTurn(true);

            // 테이블에 SET 멜드 1개 추가
            Card setCard = new Card(Suit.SPADE, Rank.KING);
            List<Card> meldActual = new ArrayList<>(List.of(
                    setCard,
                    new Card(Suit.HEART, Rank.KING),
                    new Card(Suit.DIAMOND, Rank.KING)));
            List<DeclaredCard> meldDeclared = toHonestDeclared(meldActual);
            Meld meld = Meld.create(meldId, PLAYER_ID, MeldType.SET, meldActual, meldDeclared, false);
            state.getTableMelds().add(meld);

            return state;
        }

        @Nested
        @DisplayName("검증")
        class Validation {

            @Test
            @DisplayName("현재 플레이어가 아닌 플레이어가 확장하면 INVALID_TURN 예외가 발생한다")
            void throws_invalid_turn_when_not_current_player() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                assertThatThrownBy(() -> roundService.handleExtend(state, "other", request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TURN));
            }

            @Test
            @DisplayName("ACTION 페이즈가 아닐 때 확장하면 INVALID_TURN_PHASE 예외가 발생한다")
            void throws_invalid_turn_phase_when_not_action_phase() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard));
                RoundState state = buildStateWithMeld(hand, "meld-1");
                state.setTurnPhase(TurnPhase.DRAW);

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                assertThatThrownBy(() -> roundService.handleExtend(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
            }

            @Test
            @DisplayName("이번 턴에 멜드를 하지 않은 플레이어가 확장하면 INVALID_EXTEND 예외가 발생한다")
            void throws_invalid_extend_when_not_melded_this_turn() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                // hasMeldedThisTurn 을 false 로 리셋
                state.getPlayerStates().get(PLAYER_ID).setHasMeldedThisTurn(false);

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                assertThatThrownBy(() -> roundService.handleExtend(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_EXTEND));
            }

            @Test
            @DisplayName("존재하지 않는 meldId 를 지정하면 MELD_NOT_FOUND 예외가 발생한다")
            void throws_meld_not_found_when_meld_id_not_exist() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("nonexistent-meld", extCards, toHonestDeclared(extCards));

                assertThatThrownBy(() -> roundService.handleExtend(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.MELD_NOT_FOUND));
            }

            @Test
            @DisplayName("손패에 없는 카드로 확장하면 INVALID_CARD 예외가 발생한다")
            void throws_invalid_card_when_card_not_in_hand() {
                // 손패에 KING 없음
                List<Card> hand = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.ACE)));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(new Card(Suit.CLUB, Rank.KING));
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                assertThatThrownBy(() -> roundService.handleExtend(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CARD));
            }

            @Test
            @DisplayName("canExtend 가 false 를 반환하면 INVALID_EXTEND 예외가 발생한다")
            void throws_invalid_extend_when_can_extend_fails() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                when(meldValidationService.canExtend(any(), any(), any())).thenReturn(false);

                assertThatThrownBy(() -> roundService.handleExtend(state, PLAYER_ID, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_EXTEND));
            }
        }

        @Nested
        @DisplayName("고잉아웃")
        class GoingOut {

            @Test
            @DisplayName("확장 후 손패가 비어지면 GOING_OUT 종료 조건이 설정된다")
            void going_out_when_hand_empty_after_extend() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                when(meldValidationService.canExtend(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleExtend(state, PLAYER_ID, request);

                assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.GOING_OUT);
                assertThat(result.getPlayerStates().get(PLAYER_ID).getHand()).isEmpty();
            }

            @Test
            @DisplayName("확장 후 손패가 남아있으면 endCondition 이 null 이다")
            void no_going_out_when_hand_not_empty_after_extend() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                Card remaining = new Card(Suit.HEART, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(extCard, remaining));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                when(meldValidationService.canExtend(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleExtend(state, PLAYER_ID, request);

                assertThat(result.getEndCondition()).isNull();
            }
        }

        @Nested
        @DisplayName("정상 처리")
        class HappyPath {

            @Test
            @DisplayName("확장 성공 시 카드가 해당 멜드의 extensions 에 누적된다")
            void extended_cards_accumulated_in_meld_extensions() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard, new Card(Suit.HEART, Rank.TWO)));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                when(meldValidationService.canExtend(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleExtend(state, PLAYER_ID, request);

                Meld meld = result.getTableMelds().get(0);
                assertThat(meld.getExtensions()).containsKey(PLAYER_ID);
                assertThat(meld.getExtensions().get(PLAYER_ID)).containsExactlyElementsOf(extCards);
            }

            @Test
            @DisplayName("같은 멜드에 두 번 확장하면 extensions 에 누적된다")
            void second_extend_on_same_meld_accumulates() {
                Card extCard1 = new Card(Suit.CLUB, Rank.KING);
                Card extCard2 = new Card(Suit.DIAMOND, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard1, extCard2));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                when(meldValidationService.canExtend(any(), any(), any())).thenReturn(true);

                // 첫 번째 확장
                List<Card> ext1 = List.of(extCard1);
                roundService.handleExtend(state, PLAYER_ID,
                        new ExtendRequest("meld-1", ext1, toHonestDeclared(ext1)));

                // 두 번째 확장
                List<Card> ext2 = List.of(extCard2);
                RoundState result = roundService.handleExtend(state, PLAYER_ID,
                        new ExtendRequest("meld-1", ext2, toHonestDeclared(ext2)));

                Meld meld = result.getTableMelds().get(0);
                assertThat(meld.getExtensions().get(PLAYER_ID))
                        .containsExactlyInAnyOrder(extCard1, extCard2);
            }

            @Test
            @DisplayName("확장 성공 시 lastDoubtableMeldId 가 해당 멜드 ID 로 설정된다")
            void last_doubtable_meld_id_updated_after_extend() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                List<Card> hand = new ArrayList<>(List.of(extCard, new Card(Suit.HEART, Rank.TWO)));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                when(meldValidationService.canExtend(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleExtend(state, PLAYER_ID, request);

                assertThat(result.getLastDoubtableMeldId()).isEqualTo("meld-1");
            }

            @Test
            @DisplayName("확장 성공 시 actualCards 가 손패에서 제거된다")
            void actual_cards_removed_from_hand_after_extend() {
                Card extCard = new Card(Suit.CLUB, Rank.KING);
                Card keepCard = new Card(Suit.HEART, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(extCard, keepCard));
                RoundState state = buildStateWithMeld(hand, "meld-1");

                List<Card> extCards = List.of(extCard);
                ExtendRequest request = new ExtendRequest("meld-1", extCards, toHonestDeclared(extCards));

                when(meldValidationService.canExtend(any(), any(), any())).thenReturn(true);

                RoundState result = roundService.handleExtend(state, PLAYER_ID, request);

                List<Card> handAfter = result.getPlayerStates().get(PLAYER_ID).getHand();
                assertThat(handAfter).doesNotContain(extCard);
                assertThat(handAfter).contains(keepCard);
            }
        }
    }

    // ----------------------------------------------------------------
    // handleDiscard 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleDiscard")
    class HandleDiscard {

        private static final String P1 = "p1";
        private static final String P2 = "p2";

        /**
         * 2인 게임 RoundState 조립 헬퍼.
         * p1 이 currentPlayer(index 0), p2 가 다음 플레이어.
         */
        private RoundState buildTwoPlayerState(String p1, String p2,
                                               TurnPhase phase, List<Card> p1Hand) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(p1, p2)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(phase);
            state.setStockPile(new ArrayList<>());
            state.setDiscardPile(makeCards(1));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());

            Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
            for (String pid : List.of(p1, p2)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(pid.equals(p1) ? new ArrayList<>(p1Hand) : makeCards(5));
                prs.setStatus(PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(false);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                playerStates.put(pid, prs);
            }
            state.setPlayerStates(playerStates);
            return state;
        }

        /**
         * 3인 게임 RoundState 조립 헬퍼 (ELIMINATED 플레이어 건너뛰기 테스트용).
         * turnOrder = [p1, p2, p3], p1 이 currentPlayer.
         */
        private RoundState buildThreePlayerState(String p1, String p2, String p3,
                                                 PlayerStatus p2Status,
                                                 List<Card> p1Hand) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(p1, p2, p3)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.ACTION);
            state.setStockPile(new ArrayList<>());
            state.setDiscardPile(makeCards(1));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());

            Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
            for (String pid : List.of(p1, p2, p3)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(pid.equals(p1) ? new ArrayList<>(p1Hand) : makeCards(3));
                prs.setStatus(pid.equals(p2) ? p2Status : PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(false);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                playerStates.put(pid, prs);
            }
            state.setPlayerStates(playerStates);
            return state;
        }

        @Nested
        @DisplayName("검증")
        class Validation {

            @Test
            @DisplayName("현재 플레이어가 아닌 플레이어가 버리기를 시도하면 INVALID_TURN 예외가 발생한다")
            void throws_invalid_turn_when_not_current_player() {
                List<Card> hand = makeCards(3);
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), makeCards(1), hand);

                Card card = hand.get(0);
                DiscardRequest request = new DiscardRequest(card);

                assertThatThrownBy(() -> roundService.handleDiscard(state, "other", request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TURN));
            }

            @Test
            @DisplayName("DRAW 페이즈에서 버리기를 시도하면 INVALID_TURN_PHASE 예외가 발생한다")
            void throws_invalid_turn_phase_when_draw_phase() {
                List<Card> hand = makeCards(3);
                RoundState state = buildState(P1, TurnPhase.DRAW,
                        makeCards(5), makeCards(1), hand);

                Card card = hand.get(0);
                DiscardRequest request = new DiscardRequest(card);

                assertThatThrownBy(() -> roundService.handleDiscard(state, P1, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
            }

            @Test
            @DisplayName("손패에 없는 카드를 버리려 하면 INVALID_CARD 예외가 발생한다")
            void throws_invalid_card_when_card_not_in_hand() {
                List<Card> hand = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.ACE)));
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), makeCards(1), hand);

                Card notInHand = new Card(Suit.HEART, Rank.KING);
                DiscardRequest request = new DiscardRequest(notInHand);

                assertThatThrownBy(() -> roundService.handleDiscard(state, P1, request))
                        .isInstanceOf(GameException.class)
                        .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CARD));
            }
        }

        @Nested
        @DisplayName("고잉아웃")
        class GoingOut {

            @Test
            @DisplayName("마지막 1장을 버리면 endCondition 이 GOING_OUT 이 되고 손패가 비어있다")
            void going_out_when_last_card_discarded() {
                Card lastCard = new Card(Suit.SPADE, Rank.ACE);
                List<Card> hand = new ArrayList<>(List.of(lastCard));
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), makeCards(1), hand);

                DiscardRequest request = new DiscardRequest(lastCard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.GOING_OUT);
                assertThat(result.getPlayerStates().get(P1).getHand()).isEmpty();
            }

            @Test
            @DisplayName("마지막 1장을 버릴 때 thankYouTimerSec 는 5로 설정되지 않는다")
            void timer_not_set_to_5_when_going_out() {
                Card lastCard = new Card(Suit.SPADE, Rank.ACE);
                List<Card> hand = new ArrayList<>(List.of(lastCard));
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), makeCards(1), hand);
                state.setThankYouTimerSec(0);

                DiscardRequest request = new DiscardRequest(lastCard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                assertThat(result.getThankYouTimerSec()).isNotEqualTo(5);
            }

            @Test
            @DisplayName("마지막 1장을 버릴 때 turnPhase 는 ACTION 그대로이고 currentPlayerIndex 는 변하지 않는다")
            void turn_not_advanced_when_going_out() {
                Card lastCard = new Card(Suit.SPADE, Rank.ACE);
                List<Card> hand = new ArrayList<>(List.of(lastCard));
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), makeCards(1), hand);
                int indexBefore = state.getCurrentPlayerIndex();

                DiscardRequest request = new DiscardRequest(lastCard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                assertThat(result.getTurnPhase()).isEqualTo(TurnPhase.ACTION);
                assertThat(result.getCurrentPlayerIndex()).isEqualTo(indexBefore);
            }

            @Test
            @DisplayName("마지막 1장을 버려 고잉아웃 시 hasMeldedThisTurn 은 false 로 리셋된다")
            void has_melded_this_turn_reset_on_going_out() {
                Card lastCard = new Card(Suit.SPADE, Rank.ACE);
                List<Card> hand = new ArrayList<>(List.of(lastCard));
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), makeCards(1), hand);
                state.getPlayerStates().get(P1).setHasMeldedThisTurn(true);

                DiscardRequest request = new DiscardRequest(lastCard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.GOING_OUT);
                assertThat(result.getPlayerStates().get(P1).isHasMeldedThisTurn()).isFalse();
            }
        }

        @Nested
        @DisplayName("정상 처리")
        class HappyPath {

            @Test
            @DisplayName("버린 카드가 손패에서 제거되고 버림더미 끝(상단)에 추가된다")
            void card_removed_from_hand_and_added_to_discard_top() {
                Card discard = new Card(Suit.HEART, Rank.KING);
                Card keep = new Card(Suit.CLUB, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(discard, keep));
                List<Card> pile = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.THREE)));
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), pile, hand);

                DiscardRequest request = new DiscardRequest(discard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                assertThat(result.getPlayerStates().get(P1).getHand()).doesNotContain(discard);
                assertThat(result.getPlayerStates().get(P1).getHand()).contains(keep);
                List<Card> resultPile = result.getDiscardPile();
                assertThat(resultPile.get(resultPile.size() - 1)).isEqualTo(discard);
            }

            @Test
            @DisplayName("정상 버리기 후 thankYouTimerSec 가 5로 설정된다")
            void thank_you_timer_set_to_5_after_normal_discard() {
                Card discard = new Card(Suit.HEART, Rank.KING);
                Card keep = new Card(Suit.CLUB, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(discard, keep));
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), makeCards(1), hand);

                DiscardRequest request = new DiscardRequest(discard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                assertThat(result.getThankYouTimerSec()).isEqualTo(5);
            }

            @Test
            @DisplayName("정상 버리기 후 turnPhase 가 DRAW 로 전환된다")
            void turn_phase_becomes_draw_after_normal_discard() {
                Card discard = new Card(Suit.HEART, Rank.KING);
                Card keep = new Card(Suit.CLUB, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(discard, keep));
                RoundState state = buildState(P1, TurnPhase.ACTION,
                        makeCards(5), makeCards(1), hand);

                DiscardRequest request = new DiscardRequest(discard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                assertThat(result.getTurnPhase()).isEqualTo(TurnPhase.DRAW);
            }

            @Test
            @DisplayName("정상 버리기 후 currentPlayerIndex 가 변경된다")
            void current_player_index_advances_after_normal_discard() {
                Card discard = new Card(Suit.HEART, Rank.KING);
                Card keep = new Card(Suit.CLUB, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(discard, keep));
                RoundState state = buildTwoPlayerState(P1, P2, TurnPhase.ACTION, hand);
                int indexBefore = state.getCurrentPlayerIndex();

                DiscardRequest request = new DiscardRequest(discard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                assertThat(result.getCurrentPlayerIndex()).isNotEqualTo(indexBefore);
            }

            @Test
            @DisplayName("2인 게임에서 p1 이 버리면 currentPlayer 가 p2 가 된다")
            void current_player_becomes_p2_after_p1_discards_in_two_player_game() {
                Card discard = new Card(Suit.HEART, Rank.KING);
                Card keep = new Card(Suit.CLUB, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(discard, keep));
                RoundState state = buildTwoPlayerState(P1, P2, TurnPhase.ACTION, hand);

                DiscardRequest request = new DiscardRequest(discard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                String currentPlayer = result.getTurnOrder().get(result.getCurrentPlayerIndex());
                assertThat(currentPlayer).isEqualTo(P2);
            }
        }

        @Nested
        @DisplayName("턴 전진 - 멀티 플레이어")
        class TurnAdvance {

            @Test
            @DisplayName("다음 순서 플레이어가 ELIMINATED 이면 그 다음 ACTIVE 플레이어로 건너뛴다")
            void skips_eliminated_player_when_advancing_turn() {
                // turnOrder = [p1, p2, p3], p2 가 ELIMINATED
                // p1 이 버리면 p2를 건너뛰고 p3 가 currentPlayer 가 되어야 한다
                Card discard = new Card(Suit.HEART, Rank.KING);
                Card keep = new Card(Suit.CLUB, Rank.TWO);
                List<Card> hand = new ArrayList<>(List.of(discard, keep));
                RoundState state = buildThreePlayerState(P1, P2, "p3",
                        PlayerStatus.ELIMINATED, hand);

                DiscardRequest request = new DiscardRequest(discard);
                RoundState result = roundService.handleDiscard(state, P1, request);

                String currentPlayer = result.getTurnOrder().get(result.getCurrentPlayerIndex());
                assertThat(currentPlayer).isEqualTo("p3");
            }
        }
    }


    // ----------------------------------------------------------------
    // handleThankYou 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleThankYou")
    class HandleThankYou {

        private static final String P1 = "p1";
        private static final String P2 = "p2";

        private RoundState buildThankYouState(int timerSec, boolean discardEmpty) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(P1, P2)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.DRAW);
            state.setStockPile(makeCards(5));
            state.setDiscardPile(discardEmpty ? new ArrayList<>() : makeCards(2));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());
            state.setThankYouTimerSec(timerSec);
            state.setLastDoubtableMeldId("some-meld");

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            for (String pid : List.of(P1, P2)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(makeCards(3));
                prs.setStatus(PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(true);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                ps.put(pid, prs);
            }
            state.setPlayerStates(ps);
            return state;
        }

        @Test
        @DisplayName("thankYouTimerSec 가 0 이면 INVALID_TURN_PHASE 예외가 발생한다")
        void throws_invalid_turn_phase_when_timer_zero() {
            RoundState state = buildThankYouState(0, false);

            assertThatThrownBy(() -> roundService.handleThankYou(state, P2))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("thankYouTimerSec 가 음수이면 INVALID_TURN_PHASE 예외가 발생한다")
        void throws_invalid_turn_phase_when_timer_negative() {
            RoundState state = buildThankYouState(-1, false);

            assertThatThrownBy(() -> roundService.handleThankYou(state, P2))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("playerStates 에 없는 플레이어이면 INVALID_TURN 예외가 발생한다")
        void throws_invalid_turn_when_player_not_in_states() {
            RoundState state = buildThankYouState(5, false);

            assertThatThrownBy(() -> roundService.handleThankYou(state, "unknown"))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN));
        }

        @Test
        @DisplayName("플레이어 상태가 ELIMINATED 이면 INVALID_TURN 예외가 발생한다")
        void throws_invalid_turn_when_player_eliminated() {
            RoundState state = buildThankYouState(5, false);
            state.getPlayerStates().get(P2).setStatus(PlayerStatus.ELIMINATED);

            assertThatThrownBy(() -> roundService.handleThankYou(state, P2))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN));
        }

        @Test
        @DisplayName("버림더미가 비어있으면 INVALID_CARD 예외가 발생한다")
        void throws_invalid_card_when_discard_pile_empty() {
            RoundState state = buildThankYouState(5, true);

            assertThatThrownBy(() -> roundService.handleThankYou(state, P2))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_CARD));
        }

        @Test
        @DisplayName("정상: 버림더미 상단 카드가 선언자 손패에 추가된다")
        void happy_path_top_discard_moved_to_declarant_hand() {
            Card topCard = new Card(Suit.HEART, Rank.KING);
            RoundState state = buildThankYouState(5, false);
            state.getDiscardPile().add(topCard);

            int handSizeBefore = state.getPlayerStates().get(P2).getHand().size();
            roundService.handleThankYou(state, P2);

            List<Card> hand = state.getPlayerStates().get(P2).getHand();
            assertThat(hand).hasSize(handSizeBefore + 1);
            assertThat(hand).contains(topCard);
            assertThat(state.getDiscardPile()).doesNotContain(topCard);
        }

        @Test
        @DisplayName("정상: currentPlayerIndex 가 선언자 인덱스로 변경된다")
        void happy_path_current_player_index_set_to_declarant() {
            RoundState state = buildThankYouState(5, false);
            roundService.handleThankYou(state, P2);

            assertThat(state.getCurrentPlayerIndex()).isEqualTo(1);
        }

        @Test
        @DisplayName("정상: turnPhase 가 ACTION 으로 설정된다")
        void happy_path_turn_phase_set_to_action() {
            RoundState state = buildThankYouState(5, false);
            roundService.handleThankYou(state, P2);

            assertThat(state.getTurnPhase()).isEqualTo(TurnPhase.ACTION);
        }

        @Test
        @DisplayName("정상: thankYouTimerSec 가 0 으로 초기화된다")
        void happy_path_thank_you_timer_reset_to_zero() {
            RoundState state = buildThankYouState(5, false);
            roundService.handleThankYou(state, P2);

            assertThat(state.getThankYouTimerSec()).isEqualTo(0);
        }

        @Test
        @DisplayName("정상: lastDoubtableMeldId 가 null 로 초기화된다")
        void happy_path_last_doubtable_meld_id_reset_to_null() {
            RoundState state = buildThankYouState(5, false);
            roundService.handleThankYou(state, P2);

            assertThat(state.getLastDoubtableMeldId()).isNull();
        }

        @Test
        @DisplayName("정상: 선언자의 hasMeldedThisTurn 이 false 로 초기화된다")
        void happy_path_declarant_has_melded_this_turn_reset() {
            RoundState state = buildThankYouState(5, false);
            state.getPlayerStates().get(P2).setHasMeldedThisTurn(true);

            roundService.handleThankYou(state, P2);

            assertThat(state.getPlayerStates().get(P2).isHasMeldedThisTurn()).isFalse();
        }

        @Test
        @DisplayName("현재 플레이어(P1)가 아닌 ACTIVE 플레이어(P2)도 땡큐 선언이 가능하다")
        void any_active_player_can_declare_thank_you() {
            RoundState state = buildThankYouState(GameConstants.THANK_YOU_TIMER_DISCARD_SEC, false);

            RoundState result = roundService.handleThankYou(state, P2);

            assertThat(result).isNotNull();
            assertThat(result.getTurnPhase()).isEqualTo(TurnPhase.ACTION);
        }
    }

    // ----------------------------------------------------------------
    // handleStop 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleStop")
    class HandleStop {

        private static final String PLAYER_ID = "p1";

        @Test
        @DisplayName("현재 플레이어가 아닌 플레이어가 스탑 선언하면 INVALID_TURN 예외가 발생한다")
        void throws_invalid_turn_when_not_current_player() {
            RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                    makeCards(5), makeCards(2), makeCards(4));

            assertThatThrownBy(() -> roundService.handleStop(state, "other", new StopRequest()))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN));
        }

        @Test
        @DisplayName("DRAW 페이즈가 아닐 때 스탑 선언하면 INVALID_TURN_PHASE 예외가 발생한다")
        void throws_invalid_turn_phase_when_not_draw_phase() {
            RoundState state = buildState(PLAYER_ID, TurnPhase.ACTION,
                    makeCards(5), makeCards(2), makeCards(4));

            assertThatThrownBy(() -> roundService.handleStop(state, PLAYER_ID, new StopRequest()))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("핸드 점수가 10점 초과이면 CANNOT_STOP 예외가 발생한다")
        void throws_cannot_stop_when_hand_score_exceeds_threshold() {
            RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                    makeCards(5), makeCards(2), makeCards(4));
            when(scoreService.calculateHandScore(any())).thenReturn(11);

            assertThatThrownBy(() -> roundService.handleStop(state, PLAYER_ID, new StopRequest()))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CANNOT_STOP));
        }

        @Test
        @DisplayName("핸드 점수가 정확히 10점이면 스탑 선언이 성공한다")
        void success_when_hand_score_equals_threshold() {
            RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                    makeCards(5), makeCards(2), makeCards(4));
            when(scoreService.calculateHandScore(any())).thenReturn(GameConstants.STOP_SCORE_THRESHOLD);

            RoundState result = roundService.handleStop(state, PLAYER_ID, new StopRequest());

            assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.STOP);
            assertThat(result.getPlayerStates().get(PLAYER_ID).isHasDeclaredStop()).isTrue();
        }

        @Test
        @DisplayName("핸드 점수가 0점이면 스탑 선언이 성공한다")
        void success_when_hand_score_is_zero() {
            RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                    makeCards(5), makeCards(2), makeCards(4));
            when(scoreService.calculateHandScore(any())).thenReturn(0);

            RoundState result = roundService.handleStop(state, PLAYER_ID, new StopRequest());

            assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.STOP);
        }

        @Test
        @DisplayName("스탑 선언 성공 시 endCondition 이 STOP 이 된다")
        void end_condition_is_stop_after_declaration() {
            RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                    makeCards(5), makeCards(2), makeCards(4));
            when(scoreService.calculateHandScore(any())).thenReturn(5);

            RoundState result = roundService.handleStop(state, PLAYER_ID, new StopRequest());

            assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.STOP);
        }

        @Test
        @DisplayName("스탑 선언 성공 시 hasDeclaredStop 이 true 로 설정된다")
        void has_declared_stop_is_true_after_declaration() {
            RoundState state = buildState(PLAYER_ID, TurnPhase.DRAW,
                    makeCards(5), makeCards(2), makeCards(4));
            when(scoreService.calculateHandScore(any())).thenReturn(5);

            RoundState result = roundService.handleStop(state, PLAYER_ID, new StopRequest());

            assertThat(result.getPlayerStates().get(PLAYER_ID).isHasDeclaredStop()).isTrue();
        }

    }

    // ----------------------------------------------------------------
    // handleDoubt 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleDoubt")
    class HandleDoubt {

        private static final String P1 = "p1";
        private static final String P2 = "p2";

        private RoundState buildTwoPlayerActionStateForDoubt(List<Card> p1Hand) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(P1, P2)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.ACTION);
            state.setStockPile(makeCards(5));
            state.setDiscardPile(makeCards(2));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());
            state.setLastDoubtableMeldId(null);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            for (String pid : List.of(P1, P2)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(pid.equals(P1) ? new ArrayList<>(p1Hand) : makeCards(4));
                prs.setStatus(PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(false);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                ps.put(pid, prs);
            }
            state.setPlayerStates(ps);
            return state;
        }

        @Test
        @DisplayName("현재 플레이어가 아닌 플레이어가 거짓말 지목하면 INVALID_TURN 예외가 발생한다")
        void throws_invalid_turn_when_not_current_player() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(4));
            state.setLastDoubtableMeldId("meld-1");

            assertThatThrownBy(() -> roundService.handleDoubt(state, P2, new DoubtRequest("meld-1")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN));
        }

        @Test
        @DisplayName("ACTION 페이즈가 아닐 때 거짓말 지목하면 INVALID_TURN_PHASE 예외가 발생한다")
        void throws_invalid_turn_phase_when_not_action_phase() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(4));
            state.setTurnPhase(TurnPhase.DRAW);
            state.setLastDoubtableMeldId("meld-1");

            assertThatThrownBy(() -> roundService.handleDoubt(state, P1, new DoubtRequest("meld-1")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("lastDoubtableMeldId 가 null 이면 CANNOT_DOUBT 예외가 발생한다")
        void throws_cannot_doubt_when_last_doubtable_meld_id_is_null() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(4));
            state.setLastDoubtableMeldId(null);

            assertThatThrownBy(() -> roundService.handleDoubt(state, P1, new DoubtRequest("meld-1")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CANNOT_DOUBT));
        }

        @Test
        @DisplayName("요청한 meldId 가 lastDoubtableMeldId 와 다르면 CANNOT_DOUBT 예외가 발생한다")
        void throws_cannot_doubt_when_meld_id_not_matching() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(4));
            state.setLastDoubtableMeldId("meld-1");

            assertThatThrownBy(() -> roundService.handleDoubt(state, P1, new DoubtRequest("meld-2")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CANNOT_DOUBT));
        }

        @Test
        @DisplayName("지목 성공(거짓말 멜드): 소유자가 actualCards 를 돌려받고 스톡에서 1장 드로우한다")
        void doubt_success_owner_gets_actual_cards_back_and_draws() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);
            state.setLastDoubtableMeldId("meld-1");

            int ownerHandBefore = state.getPlayerStates().get(P2).getHand().size();
            roundService.handleDoubt(state, P1, new DoubtRequest("meld-1"));

            // 소유자: actualCards 회수 + 스톡 1장 드로우
            assertThat(state.getPlayerStates().get(P2).getHand())
                    .hasSize(ownerHandBefore + actualCards.size() + 1);
        }

        @Test
        @DisplayName("지목 성공(거짓말 멜드): 확장자가 extension 카드를 돌려받는다")
        void doubt_success_extender_gets_extension_cards_back() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);

            Card extCard = new Card(Suit.HEART, Rank.THREE);
            meld.getExtensions().put(P1, new ArrayList<>(List.of(extCard)));
            state.getTableMelds().add(meld);
            state.setLastDoubtableMeldId("meld-1");

            roundService.handleDoubt(state, P1, new DoubtRequest("meld-1"));

            assertThat(state.getPlayerStates().get(P1).getHand()).contains(extCard);
        }

        @Test
        @DisplayName("지목 성공(거짓말 멜드): 멜드가 테이블에서 제거된다")
        void doubt_success_meld_removed_from_table() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);
            state.setLastDoubtableMeldId("meld-1");

            roundService.handleDoubt(state, P1, new DoubtRequest("meld-1"));

            assertThat(state.getTableMelds()).doesNotContain(meld);
        }

        @Test
        @DisplayName("지목 성공: lastDoubtableMeldId 가 null 로 초기화된다")
        void doubt_success_last_doubtable_meld_id_cleared() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);
            state.setLastDoubtableMeldId("meld-1");

            roundService.handleDoubt(state, P1, new DoubtRequest("meld-1"));

            assertThat(state.getLastDoubtableMeldId()).isNull();
        }

        @Test
        @DisplayName("지목 실패(정직 멜드): 지목자가 스톡에서 1장 드로우한다")
        void doubt_failure_doubter_draws_one_card() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), false);
            state.getTableMelds().add(meld);
            state.setLastDoubtableMeldId("meld-1");

            int doubterHandBefore = state.getPlayerStates().get(P1).getHand().size();
            roundService.handleDoubt(state, P1, new DoubtRequest("meld-1"));

            assertThat(state.getPlayerStates().get(P1).getHand()).hasSize(doubterHandBefore + 1);
        }

        @Test
        @DisplayName("지목 실패: lastDoubtableMeldId 가 null 로 초기화된다")
        void doubt_failure_last_doubtable_meld_id_cleared() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), false);
            state.getTableMelds().add(meld);
            state.setLastDoubtableMeldId("meld-1");

            roundService.handleDoubt(state, P1, new DoubtRequest("meld-1"));

            assertThat(state.getLastDoubtableMeldId()).isNull();
        }

        @Test
        @DisplayName("지목 실패: 멜드가 테이블에 그대로 남아있다")
        void doubt_failure_meld_remains_on_table() {
            RoundState state = buildTwoPlayerActionStateForDoubt(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), false);
            state.getTableMelds().add(meld);
            state.setLastDoubtableMeldId("meld-1");

            roundService.handleDoubt(state, P1, new DoubtRequest("meld-1"));

            assertThat(state.getTableMelds()).contains(meld);
        }
    }

    // ----------------------------------------------------------------
    // handleRevealBluff 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleRevealBluff")
    class HandleRevealBluff {

        private static final String P1 = "p1";
        private static final String P2 = "p2";

        private RoundState buildTwoPlayerActionStateForReveal(List<Card> p1Hand) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(P1, P2)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.ACTION);
            state.setStockPile(makeCards(5));
            state.setDiscardPile(makeCards(2));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());
            state.setLastDoubtableMeldId(null);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            for (String pid : List.of(P1, P2)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(pid.equals(P1) ? new ArrayList<>(p1Hand) : makeCards(4));
                prs.setStatus(PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(false);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                ps.put(pid, prs);
            }
            state.setPlayerStates(ps);
            return state;
        }

        @Test
        @DisplayName("현재 플레이어가 아닌 플레이어가 공개하면 INVALID_TURN 예외가 발생한다")
        void throws_invalid_turn_when_not_current_player() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);

            assertThatThrownBy(() -> roundService.handleRevealBluff(state, P2, new RevealBluffRequest("meld-1")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN));
        }

        @Test
        @DisplayName("ACTION 페이즈가 아닐 때 공개하면 INVALID_TURN_PHASE 예외가 발생한다")
        void throws_invalid_turn_phase_when_not_action_phase() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            state.setTurnPhase(TurnPhase.DRAW);
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P1, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);

            assertThatThrownBy(() -> roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("존재하지 않는 meldId 이면 MELD_NOT_FOUND 예외가 발생한다")
        void throws_meld_not_found_when_meld_id_invalid() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));

            assertThatThrownBy(() -> roundService.handleRevealBluff(state, P1, new RevealBluffRequest("nonexistent")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.MELD_NOT_FOUND));
        }

        @Test
        @DisplayName("소유자가 아닌 플레이어가 공개하려 하면 INVALID_MELD 예외가 발생한다")
        void throws_invalid_meld_when_not_owner() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P2, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);

            assertThatThrownBy(() -> roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_MELD));
        }

        @Test
        @DisplayName("거짓말 멜드가 아닌 멜드를 공개하려 하면 INVALID_BLUFF 예외가 발생한다")
        void throws_invalid_bluff_when_meld_is_not_bluff() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P1, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), false);
            state.getTableMelds().add(meld);

            assertThatThrownBy(() -> roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1")))
                    .isInstanceOf(GameException.class)
                    .satisfies(e -> assertThat(((GameException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_BLUFF));
        }

        @Test
        @DisplayName("정상: 소유자가 actualCards 를 돌려받는다")
        void happy_path_owner_gets_actual_cards_back() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            Card bluffCard = new Card(Suit.SPADE, Rank.TWO);
            List<Card> actualCards = new ArrayList<>(List.of(bluffCard));
            Meld meld = Meld.create("meld-1", P1, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);

            int ownerHandBefore = state.getPlayerStates().get(P1).getHand().size();
            roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1"));

            assertThat(state.getPlayerStates().get(P1).getHand()).hasSize(ownerHandBefore + 1);
            assertThat(state.getPlayerStates().get(P1).getHand()).contains(bluffCard);
        }

        @Test
        @DisplayName("정상: 확장자는 extension 카드를 돌려받지 않고 스톡에서 1장 드로우만 한다 (ruleBook §7)")
        void happy_path_extender_only_draws_penalty_no_card_return() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P1, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);

            Card extCard = new Card(Suit.HEART, Rank.THREE);
            meld.getExtensions().put(P2, new ArrayList<>(List.of(extCard)));
            state.getTableMelds().add(meld);

            int extenderHandBefore = state.getPlayerStates().get(P2).getHand().size();
            roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1"));

            // 확장자: 카드 회수 없이 패널티 드로우 1장만 (ruleBook §7)
            assertThat(state.getPlayerStates().get(P2).getHand())
                    .hasSize(extenderHandBefore + 1);
            assertThat(state.getPlayerStates().get(P2).getHand()).doesNotContain(extCard);
        }

        @Test
        @DisplayName("정상: 멜드가 테이블에서 제거된다")
        void happy_path_meld_removed_from_table() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P1, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);

            roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1"));

            assertThat(state.getTableMelds()).doesNotContain(meld);
        }

        @Test
        @DisplayName("정상: lastDoubtableMeldId 가 null 로 초기화된다")
        void happy_path_last_doubtable_meld_id_cleared() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P1, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);
            state.setLastDoubtableMeldId("meld-1");

            roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1"));

            assertThat(state.getLastDoubtableMeldId()).isNull();
        }

        @Test
        @DisplayName("자진 공개 멜드가 lastDoubtableMeldId 와 달라도 항상 null 로 초기화된다 (S-3)")
        void reveal_bluff_always_clears_last_doubtable_meld_id_even_when_different() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P1, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            state.getTableMelds().add(meld);
            // lastDoubtableMeldId 가 다른 멜드를 가리키도록 설정
            state.setLastDoubtableMeldId("other-meld-id");

            roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1"));

            assertThat(state.getLastDoubtableMeldId()).isNull();
        }

        @Test
        @DisplayName("확장자가 없을 때: 소유자만 카드를 돌려받고 P2 손패에 변화가 없다")
        void happy_path_no_extenders_only_owner_gets_cards_back() {
            RoundState state = buildTwoPlayerActionStateForReveal(makeCards(3));
            List<Card> actualCards = new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.TWO)));
            Meld meld = Meld.create("meld-1", P1, MeldType.SOLO_SEVEN,
                    actualCards, toHonestDeclared(actualCards), true);
            // extensions 는 빈 맵
            state.getTableMelds().add(meld);

            int p2HandBefore = state.getPlayerStates().get(P2).getHand().size();
            roundService.handleRevealBluff(state, P1, new RevealBluffRequest("meld-1"));

            assertThat(state.getPlayerStates().get(P2).getHand()).hasSize(p2HandBefore);
        }
    }


    // ----------------------------------------------------------------
    // resolveRound 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("resolveRound")
    class ResolveRound {

        private static final String P1 = "p1";
        private static final String P2 = "p2";
        private static final String P3 = "p3";

        /** endCondition 과 playerStates 를 직접 지정하여 RoundState 를 조립한다 */
        private RoundState buildResolveState(RoundEndCondition endCondition,
                                             Map<String, PlayerRoundState> playerStates) {
            RoundState state = new RoundState();
            state.setEndCondition(endCondition);
            state.setPlayerStates(playerStates);
            state.setTurnOrder(new ArrayList<>(playerStates.keySet()));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.ACTION);
            state.setStockPile(makeCards(5));
            state.setDiscardPile(makeCards(2));
            state.setStockRefillCount(0);
            state.setTableMelds(new ArrayList<>());
            state.setLastDoubtableMeldId(null);
            state.setThankYouTimerSec(0);
            return state;
        }

        /** PlayerRoundState 조립 헬퍼 */
        private PlayerRoundState makePlayerState(String playerId, PlayerStatus status,
                                                  List<Card> hand) {
            PlayerRoundState prs = new PlayerRoundState();
            prs.setPlayerId(playerId);
            prs.setStatus(status);
            prs.setHand(new ArrayList<>(hand));
            prs.setHasMeldedThisTurn(false);
            prs.setHasDeclaredStop(false);
            prs.setHasBankrupted(false);
            prs.setDisconnectCount(0);
            return prs;
        }

        @Test
        @DisplayName("GOING_OUT: 손패가 빈 ACTIVE 플레이어가 승자로 결정된다")
        void going_out_active_empty_hand_is_winner() {
            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            ps.put(P1, makePlayerState(P1, PlayerStatus.ACTIVE, List.of()));   // 손패 0장
            ps.put(P2, makePlayerState(P2, PlayerStatus.ACTIVE, makeCards(3)));

            RoundState state = buildResolveState(RoundEndCondition.GOING_OUT, ps);

            when(scoreService.calculateRoundScoreDelta(any(), any(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<String> winners = inv.getArgument(1);
                        assertThat(winners).containsExactly(P1);
                        return Map.of(P1, 10, P2, -10);
                    });

            List<String> winners = roundService.determineWinners(state);
            roundService.resolveRound(state, winners);
        }

        @Test
        @DisplayName("GOING_OUT: ELIMINATED 플레이어는 손패가 비어도 승자가 되지 않는다")
        void going_out_eliminated_empty_hand_not_winner() {
            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            ps.put(P1, makePlayerState(P1, PlayerStatus.ELIMINATED, List.of())); // ELIMINATED
            ps.put(P2, makePlayerState(P2, PlayerStatus.ACTIVE, List.of()));      // ACTIVE + 빈 손패

            RoundState state = buildResolveState(RoundEndCondition.GOING_OUT, ps);

            when(scoreService.calculateRoundScoreDelta(any(), any(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<String> winners = inv.getArgument(1);
                        assertThat(winners).containsExactly(P2);
                        assertThat(winners).doesNotContain(P1);
                        return Map.of();
                    });

            List<String> winners = roundService.determineWinners(state);
            roundService.resolveRound(state, winners);
        }

        @Test
        @DisplayName("STOP: 핸드 점수가 가장 낮은 ACTIVE 플레이어가 승자가 된다")
        void stop_lowest_score_player_is_winner() {
            List<Card> p1Hand = makeCards(2);
            List<Card> p2Hand = makeCards(3);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            ps.put(P1, makePlayerState(P1, PlayerStatus.ACTIVE, p1Hand));
            ps.put(P2, makePlayerState(P2, PlayerStatus.ACTIVE, p2Hand));

            RoundState state = buildResolveState(RoundEndCondition.STOP, ps);

            when(scoreService.calculateHandScore(p1Hand)).thenReturn(5);
            when(scoreService.calculateHandScore(p2Hand)).thenReturn(15);
            when(scoreService.calculateRoundScoreDelta(any(), any(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<String> winners = inv.getArgument(1);
                        assertThat(winners).containsExactly(P1);
                        return Map.of(P1, 15, P2, -15);
                    });

            List<String> winners = roundService.determineWinners(state);
            roundService.resolveRound(state, winners);
        }

        @Test
        @DisplayName("STOP: 동점 최솟값이면 두 플레이어 모두 승자가 된다")
        void stop_tied_minimum_score_both_winners() {
            List<Card> p1Hand = makeCards(2);
            List<Card> p2Hand = makeCards(3);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            ps.put(P1, makePlayerState(P1, PlayerStatus.ACTIVE, p1Hand));
            ps.put(P2, makePlayerState(P2, PlayerStatus.ACTIVE, p2Hand));

            RoundState state = buildResolveState(RoundEndCondition.STOP, ps);

            when(scoreService.calculateHandScore(p1Hand)).thenReturn(10);
            when(scoreService.calculateHandScore(p2Hand)).thenReturn(10);
            when(scoreService.calculateRoundScoreDelta(any(), any(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<String> winners = inv.getArgument(1);
                        assertThat(winners).containsExactlyInAnyOrder(P1, P2);
                        return Map.of();
                    });

            List<String> winners = roundService.determineWinners(state);
            roundService.resolveRound(state, winners);
        }

        @Test
        @DisplayName("STOCK_DEPLETED: STOP 과 동일하게 최솟값 보유자가 승자가 된다")
        void stock_depleted_lowest_score_is_winner() {
            List<Card> p1Hand = makeCards(2);
            List<Card> p2Hand = makeCards(3);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            ps.put(P1, makePlayerState(P1, PlayerStatus.ACTIVE, p1Hand));
            ps.put(P2, makePlayerState(P2, PlayerStatus.ACTIVE, p2Hand));

            RoundState state = buildResolveState(RoundEndCondition.STOCK_DEPLETED, ps);

            when(scoreService.calculateHandScore(p1Hand)).thenReturn(3);
            when(scoreService.calculateHandScore(p2Hand)).thenReturn(20);
            when(scoreService.calculateRoundScoreDelta(any(), any(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<String> winners = inv.getArgument(1);
                        assertThat(winners).containsExactly(P1);
                        return Map.of(P1, 20, P2, -20);
                    });

            List<String> winners = roundService.determineWinners(state);
            roundService.resolveRound(state, winners);
        }

        @Test
        @DisplayName("BANKRUPTCY: ACTIVE 플레이어 전원이 승자이고 ELIMINATED 는 제외된다")
        void bankruptcy_all_active_players_are_winners() {
            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            ps.put(P1, makePlayerState(P1, PlayerStatus.ACTIVE, makeCards(3)));
            ps.put(P2, makePlayerState(P2, PlayerStatus.ACTIVE, makeCards(4)));
            ps.put(P3, makePlayerState(P3, PlayerStatus.ELIMINATED, makeCards(10)));

            RoundState state = buildResolveState(RoundEndCondition.BANKRUPTCY, ps);

            when(scoreService.calculateRoundScoreDelta(any(), any(), any()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        List<String> winners = inv.getArgument(1);
                        assertThat(winners).containsExactlyInAnyOrder(P1, P2);
                        assertThat(winners).doesNotContain(P3);
                        return Map.of(P1, 5, P2, 5, P3, -10);
                    });

            List<String> winners = roundService.determineWinners(state);
            roundService.resolveRound(state, winners);
        }

        @Test
        @DisplayName("반환값은 scoreService.calculateRoundScoreDelta 가 반환한 Map 그대로이다")
        void return_value_equals_score_service_result() {
            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            ps.put(P1, makePlayerState(P1, PlayerStatus.ACTIVE, List.of()));
            ps.put(P2, makePlayerState(P2, PlayerStatus.ACTIVE, makeCards(3)));

            RoundState state = buildResolveState(RoundEndCondition.GOING_OUT, ps);

            Map<String, Integer> expectedDelta = Map.of(P1, 20, P2, -20);
            when(scoreService.calculateRoundScoreDelta(any(), any(), any()))
                    .thenReturn(expectedDelta);

            List<String> winners = roundService.determineWinners(state);
            Map<String, Integer> result = roundService.resolveRound(state, winners);

            assertThat(result).isEqualTo(expectedDelta);
        }
    }

    // ----------------------------------------------------------------
    // BankruptcyEndCondition 테스트 (handleDraw 를 통한 간접 테스트)
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("BankruptcyEndCondition")
    class BankruptcyEndCondition {

        private static final String P1 = "p1";
        private static final String P2 = "p2";
        private static final String P3 = "p3";

        /**
         * 2인 게임: p1 이 DRAW 단계, p1 손패는 p1Hand, p2 손패는 4장.
         * 스톡에 카드가 있어 드로우 가능.
         */
        private RoundState buildTwoPlayerDrawState(String p1, String p2, List<Card> p1Hand) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(p1, p2)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.DRAW);
            state.setStockPile(makeCards(5));
            state.setDiscardPile(makeCards(2));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());
            state.setLastDoubtableMeldId(null);
            state.setThankYouTimerSec(0);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            for (String pid : List.of(p1, p2)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(pid.equals(p1) ? new ArrayList<>(p1Hand) : makeCards(4));
                prs.setStatus(PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(false);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                ps.put(pid, prs);
            }
            state.setPlayerStates(ps);
            return state;
        }

        /**
         * 3인 게임: p1 이 DRAW 단계, p1 손패는 p1Hand, p2/p3 손패는 4장.
         */
        private RoundState buildThreePlayerDrawState(String p1, String p2, String p3,
                                                      List<Card> p1Hand) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(p1, p2, p3)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.DRAW);
            state.setStockPile(makeCards(5));
            state.setDiscardPile(makeCards(2));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());
            state.setLastDoubtableMeldId(null);
            state.setThankYouTimerSec(0);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            for (String pid : List.of(p1, p2, p3)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(pid.equals(p1) ? new ArrayList<>(p1Hand) : makeCards(4));
                prs.setStatus(PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(false);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                ps.put(pid, prs);
            }
            state.setPlayerStates(ps);
            return state;
        }

        @Test
        @DisplayName("2인 게임: p1 이 파산하면 p2 만 ACTIVE 남아 endCondition 이 BANKRUPTCY 가 된다")
        void two_player_bankruptcy_sets_end_condition() {
            // p1 이 9장을 들고 있고 STOCK 드로우로 10장이 되면 파산
            RoundState state = buildTwoPlayerDrawState(P1, P2, makeCards(9));

            roundService.handleDraw(state, P1, new DrawRequest(DrawSource.STOCK));

            assertThat(state.getPlayerStates().get(P1).getStatus())
                    .isEqualTo(PlayerStatus.ELIMINATED);
            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.BANKRUPTCY);
        }

        @Test
        @DisplayName("3인 게임: p1 이 파산해도 p2, p3 가 ACTIVE 이면 endCondition 이 null 이다")
        void three_player_bankruptcy_does_not_set_end_condition() {
            // p1 이 9장 → 드로우 후 10장 → 파산, 그러나 p2/p3 아직 ACTIVE
            RoundState state = buildThreePlayerDrawState(P1, P2, P3, makeCards(9));

            roundService.handleDraw(state, P1, new DrawRequest(DrawSource.STOCK));

            assertThat(state.getPlayerStates().get(P1).getStatus())
                    .isEqualTo(PlayerStatus.ELIMINATED);
            assertThat(state.getEndCondition()).isNull();
        }

        @Test
        @DisplayName("endCondition 이 이미 GOING_OUT 이면 파산 체크로 덮어쓰지 않는다")
        void bankruptcy_check_does_not_overwrite_existing_end_condition() {
            // endCondition 을 미리 GOING_OUT 으로 설정
            RoundState state = buildTwoPlayerDrawState(P1, P2, makeCards(9));
            state.setEndCondition(RoundEndCondition.GOING_OUT);

            roundService.handleDraw(state, P1, new DrawRequest(DrawSource.STOCK));

            // GOING_OUT 이 유지되어야 한다
            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.GOING_OUT);
        }
    }


    // ----------------------------------------------------------------
    // HandleTurnTimeout 테스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleTurnTimeout")
    class HandleTurnTimeout {

        private static final String P1 = "p1";
        private static final String P2 = "p2";

        // ----------------------------------------------------------------
        // 헬퍼
        // ----------------------------------------------------------------

        /** 2인 게임 기본 헬퍼: p1 이 currentPlayer(index 0), 지정 phase */
        private RoundState buildTimeoutState(TurnPhase phase, List<Card> p1Hand,
                                             List<Card> stock) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(P1, P2)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(phase);
            state.setStockPile(new ArrayList<>(stock));
            state.setDiscardPile(makeCards(2));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());
            state.setLastDoubtableMeldId(null);
            state.setThankYouTimerSec(0);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            for (String pid : List.of(P1, P2)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(pid.equals(P1) ? new ArrayList<>(p1Hand) : makeCards(4));
                prs.setStatus(PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(false);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                ps.put(pid, prs);
            }
            state.setPlayerStates(ps);
            return state;
        }

        /**
         * 2인 게임: p1 이 DRAW 단계, p1 손패 9장, 스톡 5장 -- 파산 테스트용.
         */
        private RoundState buildTwoPlayerDrawState9Cards(String p1, String p2) {
            RoundState state = new RoundState();
            state.setTurnOrder(new ArrayList<>(List.of(p1, p2)));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.DRAW);
            state.setStockPile(makeCards(5));
            state.setDiscardPile(makeCards(2));
            state.setStockRefillCount(0);
            state.setEndCondition(null);
            state.setTableMelds(new ArrayList<>());
            state.setLastDoubtableMeldId(null);
            state.setThankYouTimerSec(0);

            Map<String, PlayerRoundState> ps = new LinkedHashMap<>();
            for (String pid : List.of(p1, p2)) {
                PlayerRoundState prs = new PlayerRoundState();
                prs.setPlayerId(pid);
                prs.setHand(pid.equals(p1) ? makeCards(9) : makeCards(4));
                prs.setStatus(PlayerStatus.ACTIVE);
                prs.setHasMeldedThisTurn(false);
                prs.setHasDeclaredStop(false);
                prs.setHasBankrupted(false);
                prs.setDisconnectCount(0);
                ps.put(pid, prs);
            }
            state.setPlayerStates(ps);
            return state;
        }

        // ----------------------------------------------------------------
        // 테스트 케이스
        // ----------------------------------------------------------------

        @Test
        @DisplayName("endCondition 이 이미 설정된 경우 즉시 반환하며 상태가 변경되지 않는다")
        void returns_immediately_when_end_condition_already_set() {
            List<Card> hand = List.of(
                    new Card(Suit.SPADE, Rank.KING),
                    new Card(Suit.HEART, Rank.QUEEN)
            );
            RoundState state = buildTimeoutState(TurnPhase.DRAW, hand, makeCards(5));
            state.setEndCondition(RoundEndCondition.GOING_OUT);
            int originalHandSize = state.getPlayerStates().get(P1).getHand().size();

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getPlayerStates().get(P1).getHand()).hasSize(originalHandSize);
            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.GOING_OUT);
        }

        @Test
        @DisplayName("ELIMINATED 플레이어는 드로우/버림 없이 즉시 반환한다")
        void returns_immediately_when_player_eliminated() {
            List<Card> hand = List.of(
                    new Card(Suit.SPADE, Rank.KING),
                    new Card(Suit.HEART, Rank.QUEEN)
            );
            RoundState state = buildTimeoutState(TurnPhase.DRAW, hand, makeCards(5));
            state.getPlayerStates().get(P1).setStatus(PlayerStatus.ELIMINATED);
            int stockSizeBefore = state.getStockPile().size();

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getStockPile()).hasSize(stockSizeBefore);
            assertThat(state.getPlayerStates().get(P1).getHand()).hasSize(hand.size());
        }

        @Test
        @DisplayName("DRAW 페이즈: 스톡에서 1장 드로우 후 최고 점수 카드를 버린다")
        void draw_phase_auto_draws_then_discards_highest_card() {
            Card queen = new Card(Suit.SPADE, Rank.QUEEN);
            Card two = new Card(Suit.HEART, Rank.TWO);
            List<Card> hand = new ArrayList<>(List.of(queen, two));
            Card stockCard = new Card(Suit.CLUB, Rank.THREE);
            List<Card> stock = new ArrayList<>(List.of(stockCard));

            RoundState state = buildTimeoutState(TurnPhase.DRAW, hand, stock);

            roundService.handleTurnTimeout(state, P1);

            // 드로우(THREE=3) 후 손패 3장 -> QUEEN(12) 버림 -> 최종 2장 남음
            assertThat(state.getPlayerStates().get(P1).getHand()).hasSize(2);
            assertThat(state.getDiscardPile()).contains(queen);
        }

        @Test
        @DisplayName("ACTION 페이즈: 드로우 없이 바로 최고 점수 카드를 버린다")
        void action_phase_directly_discards_highest_card() {
            Card king = new Card(Suit.SPADE, Rank.KING);
            Card three = new Card(Suit.DIAMOND, Rank.THREE);
            List<Card> hand = new ArrayList<>(List.of(king, three));
            List<Card> stock = makeCards(5);

            RoundState state = buildTimeoutState(TurnPhase.ACTION, hand, stock);
            int stockSizeBefore = state.getStockPile().size();

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getStockPile()).hasSize(stockSizeBefore);
            assertThat(state.getDiscardPile()).contains(king);
            assertThat(state.getPlayerStates().get(P1).getHand()).hasSize(1);
        }

        @Test
        @DisplayName("KING(13)이 QUEEN(12)보다 먼저 버려진다")
        void discards_king_over_queen() {
            Card king = new Card(Suit.SPADE, Rank.KING);
            Card queen = new Card(Suit.HEART, Rank.QUEEN);
            Card ace = new Card(Suit.DIAMOND, Rank.ACE);
            List<Card> hand = new ArrayList<>(List.of(queen, ace, king));

            RoundState state = buildTimeoutState(TurnPhase.ACTION, hand, makeCards(3));

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getDiscardPile()).contains(king);
            assertThat(state.getPlayerStates().get(P1).getHand()).doesNotContain(king);
        }

        @Test
        @DisplayName("손패가 모두 SEVEN 이면 첫 번째 카드를 버린다 (폴백)")
        void discards_first_card_when_all_sevens() {
            Card seven1 = new Card(Suit.SPADE, Rank.SEVEN);
            Card seven2 = new Card(Suit.HEART, Rank.SEVEN);
            Card seven3 = new Card(Suit.DIAMOND, Rank.SEVEN);
            List<Card> hand = new ArrayList<>(List.of(seven1, seven2, seven3));

            RoundState state = buildTimeoutState(TurnPhase.ACTION, hand, makeCards(3));

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getDiscardPile()).contains(seven1);
            assertThat(state.getPlayerStates().get(P1).getHand()).hasSize(2);
        }

        @Test
        @DisplayName("버린 후 손패가 비면 endCondition 이 GOING_OUT 으로 설정된다")
        void going_out_when_last_card_discarded() {
            Card king = new Card(Suit.SPADE, Rank.KING);
            List<Card> hand = new ArrayList<>(List.of(king));

            RoundState state = buildTimeoutState(TurnPhase.ACTION, hand, makeCards(3));

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.GOING_OUT);
            assertThat(state.getPlayerStates().get(P1).getHand()).isEmpty();
        }

        @Test
        @DisplayName("정상 버림 후 thankYouTimerSec 이 5가 되고 턴이 전진한다")
        void normal_discard_sets_thank_you_timer_and_advances_turn() {
            Card king = new Card(Suit.SPADE, Rank.KING);
            Card two = new Card(Suit.HEART, Rank.TWO);
            List<Card> hand = new ArrayList<>(List.of(king, two));

            RoundState state = buildTimeoutState(TurnPhase.ACTION, hand, makeCards(3));
            int initialIndex = state.getCurrentPlayerIndex();

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getThankYouTimerSec()).isEqualTo(GameConstants.THANK_YOU_TIMER_DISCARD_SEC);
            assertThat(state.getCurrentPlayerIndex()).isNotEqualTo(initialIndex);
            assertThat(state.getTurnPhase()).isEqualTo(TurnPhase.DRAW);
        }

        @Test
        @DisplayName("DRAW 페이즈에서 스톡이 비고 stockRefillCount 가 2 이상이면 STOCK_DEPLETED 로 종료된다")
        void draw_phase_empty_stock_triggers_stock_depleted_when_refill_exhausted() {
            Card king = new Card(Suit.SPADE, Rank.KING);
            Card two = new Card(Suit.HEART, Rank.TWO);
            List<Card> hand = new ArrayList<>(List.of(king, two));
            RoundState state = buildTimeoutState(TurnPhase.DRAW, hand, new ArrayList<>());
            state.setStockRefillCount(2);

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.STOCK_DEPLETED);
        }

        @Test
        @DisplayName("DRAW 페이즈에서 9장 손패 + 1장 드로우 = 10장 파산, 2인 게임이면 BANKRUPTCY 종료")
        void draw_phase_bankruptcy_leads_to_bankruptcy_end_condition() {
            RoundState state = buildTwoPlayerDrawState9Cards(P1, P2);

            roundService.handleTurnTimeout(state, P1);

            assertThat(state.getPlayerStates().get(P1).getStatus())
                    .isEqualTo(PlayerStatus.ELIMINATED);
            assertThat(state.getEndCondition()).isEqualTo(RoundEndCondition.BANKRUPTCY);
        }
    }

}