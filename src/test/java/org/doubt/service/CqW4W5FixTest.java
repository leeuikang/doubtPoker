package org.doubt.service;

import org.doubt.constant.GameConstants;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.Rank;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Suit;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.doubt.dto.request.DrawRequest;
import org.doubt.constant.DrawSource;
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
 * CQ-W4, CQ-W5 코드 품질 수정 검증 테스트
 *
 * <p>CQ-W4: {@code RoundService.isBluffMeld} 로직이
 * {@code MeldValidationService.isBluffMeld}로 단일화되었음을 검증한다.</p>
 *
 * <p>CQ-W5: 파산 처리 공통 로직({@code applyBankruptcy})이 올바르게 동작함을
 * handleDraw·drawOneFromStock(패널티) 경로에서 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CQ-W4, CQ-W5 수정 검증")
class CqW4W5FixTest {

    @Mock
    private DeckService deckService;

    @Mock
    private ScoreService scoreService;

    @InjectMocks
    private MeldValidationService meldValidationService;

    // ----------------------------------------------------------------
    // CQ-W4: MeldValidationService.isBluffMeld
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("CQ-W4: MeldValidationService.isBluffMeld")
    class CqW4IsBluffMeld {

        @Test
        @DisplayName("실제 카드와 선언 카드가 모두 같으면 false 반환")
        void all_matching_returns_false() {
            List<Card> actual = List.of(new Card(Suit.SPADE, Rank.ACE));
            List<DeclaredCard> declared = List.of(new DeclaredCard(Suit.SPADE, Rank.ACE));

            assertThat(meldValidationService.isBluffMeld(actual, declared)).isFalse();
        }

        @Test
        @DisplayName("1장이라도 다르면 true 반환")
        void one_mismatch_returns_true() {
            List<Card> actual = List.of(new Card(Suit.SPADE, Rank.ACE));
            List<DeclaredCard> declared = List.of(new DeclaredCard(Suit.HEART, Rank.ACE));

            assertThat(meldValidationService.isBluffMeld(actual, declared)).isTrue();
        }

        @Test
        @DisplayName("여러 장 중 마지막 장만 다르면 true 반환")
        void last_card_mismatch_returns_true() {
            List<Card> actual = List.of(
                    new Card(Suit.SPADE, Rank.ACE),
                    new Card(Suit.SPADE, Rank.TWO),
                    new Card(Suit.HEART, Rank.THREE)
            );
            List<DeclaredCard> declared = List.of(
                    new DeclaredCard(Suit.SPADE, Rank.ACE),
                    new DeclaredCard(Suit.SPADE, Rank.TWO),
                    new DeclaredCard(Suit.SPADE, Rank.THREE) // 다름
            );

            assertThat(meldValidationService.isBluffMeld(actual, declared)).isTrue();
        }
    }

    // ----------------------------------------------------------------
    // CQ-W5: applyBankruptcy — handleDraw 경로 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("CQ-W5: applyBankruptcy — handleDraw 파산 처리")
    class CqW5ApplyBankruptcy {

        private RoundService roundService;

        @BeforeEach
        void setUp() {
            roundService = new RoundService(deckService, meldValidationService, scoreService);
        }

        @Test
        @DisplayName("handleDraw 후 파산 기준 달성 시 ELIMINATED 상태로 전환된다")
        void draw_causing_bankruptcy_eliminates_player() {
            RoundState state = buildStateWithBankruptPlayer("Alice", "Bob");

            // Alice 턴, DRAW 페이즈, 스톡에 카드 1장
            state.getStockPile().add(new Card(Suit.SPADE, Rank.ACE));

            DrawRequest req = new DrawRequest(DrawSource.STOCK);
            roundService.handleDraw(state, "Alice", req);

            PlayerRoundState aliceState = state.getPlayerStates().get("Alice");
            assertThat(aliceState.getStatus())
                    .as("파산 후 Alice는 ELIMINATED 상태여야 한다")
                    .isEqualTo(PlayerStatus.ELIMINATED);
            assertThat(aliceState.isHasBankrupted())
                    .as("hasBankrupted 플래그가 true여야 한다")
                    .isTrue();
        }

        @Test
        @DisplayName("handleDraw 후 파산 — 활성 플레이어 1명 이하이면 BANKRUPTCY 종료 조건이 설정된다")
        void draw_bankruptcy_sets_end_condition_when_last_active() {
            RoundState state = buildStateWithBankruptPlayer("Alice", "Bob");

            // Bob도 미리 ELIMINATED — Alice만 남은 상태에서 파산하면 게임 종료
            state.getPlayerStates().get("Bob").setStatus(PlayerStatus.ELIMINATED);
            state.getStockPile().add(new Card(Suit.SPADE, Rank.ACE));

            DrawRequest req = new DrawRequest(DrawSource.STOCK);
            roundService.handleDraw(state, "Alice", req);

            assertThat(state.getEndCondition())
                    .as("Alice까지 탈락하면 BANKRUPTCY 종료 조건이 설정되어야 한다")
                    .isEqualTo(RoundEndCondition.BANKRUPTCY);
        }

        /**
         * Alice가 이미 BANKRUPTCY_HAND_SIZE - 1 장을 보유한 상태를 반환.
         * 스톡에서 1장 더 뽑으면 파산 기준을 충족한다.
         */
        private RoundState buildStateWithBankruptPlayer(String... playerIds) {
            RoundState state = new RoundState();
            state.setTurnOrder(List.of(playerIds));
            state.setCurrentPlayerIndex(0);
            state.setTurnPhase(TurnPhase.DRAW);
            state.setStockPile(new ArrayList<>());
            state.setDiscardPile(new ArrayList<>());
            state.setTableMelds(new ArrayList<>());

            Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
            for (int i = 0; i < playerIds.length; i++) {
                PlayerRoundState prs = new PlayerRoundState();
                if (i == 0) {
                    // 첫 번째 플레이어(Alice)에게 파산 직전 카드 수 채움
                    List<Card> hand = new ArrayList<>();
                    for (int j = 0; j < GameConstants.BANKRUPTCY_HAND_SIZE - 1; j++) {
                        hand.add(new Card(Suit.SPADE, Rank.values()[j % Rank.values().length]));
                    }
                    prs.setHand(hand);
                } else {
                    prs.setHand(new ArrayList<>());
                }
                prs.setStatus(PlayerStatus.ACTIVE);
                playerStates.put(playerIds[i], prs);
            }
            state.setPlayerStates(playerStates);
            return state;
        }
    }
}
