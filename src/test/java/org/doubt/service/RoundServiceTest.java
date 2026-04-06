package org.doubt.service;

import org.doubt.constant.DrawSource;
import org.doubt.constant.ErrorCode;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.Rank;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Suit;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.doubt.dto.request.DrawRequest;
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
        prs.setHasEverMelded(false);
        prs.setHasDeclaredStop(false);
        prs.setHasBankrupted(false);
        prs.setDisconnectCount(0);

        Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
        playerStates.put(currentPlayerId, prs);
        state.setPlayerStates(playerStates);

        return state;
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
                assertThat(prs.isHasEverMelded()).isFalse();
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

            @Test
            @DisplayName("DISCARD 페이즈에서 드로우하면 INVALID_TURN_PHASE 예외가 발생한다")
            void throws_invalid_turn_phase_when_discard_phase() {
                RoundState state = buildState(PLAYER_ID, TurnPhase.DISCARD,
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
                // 파산은 해당 플레이어만 탈락 — 라운드 전체 종료 조건은 item 9에서 처리
                assertThat(result.getEndCondition()).isNull();
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
                assertThat(result.getEndCondition()).isNull();
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
                assertThat(result.getEndCondition()).isNull();
            }
        }
    }
}
