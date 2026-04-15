package org.doubt.service;

import org.doubt.constant.PlayerStatus;
import org.doubt.constant.RoundEndCondition;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CQ-I3 수정 검증 테스트
 *
 * <p>원래 문제(CQ-I3): {@code RoundService}에서 ACTIVE 플레이어를 필터링하는
 * {@code state.getPlayerStates().entrySet().stream().filter(...ACTIVE...)} 패턴이
 * {@code findGoingOutWinners}, {@code findActiveWinners}, {@code findLowestScoreWinners},
 * {@code checkBankruptcyEndCondition} 4곳에 중복되었다.</p>
 *
 * <p>수정 후: {@code activePlayerEntries(RoundState)} 헬퍼로 추출해 각 메서드가 위임한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CQ-I3 수정 검증: RoundService activePlayerEntries 헬퍼 추출")
class CqI3FixTest {

    @Mock
    private DeckService deckService;
    @Mock
    private MeldValidationService meldValidationService;
    @Mock
    private ScoreService scoreService;
    @Mock
    private GameBroadcastService gameBroadcastService;
    @Mock
    private TimerService timerService;

    @InjectMocks
    private RoundService roundService;

    // ----------------------------------------------------------------
    // findGoingOutWinners — ACTIVE + hand.isEmpty()
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("GOING_OUT: 손패가 빈 ACTIVE 플레이어만 승자")
    class FindGoingOutWinners {

        @Test
        @DisplayName("ACTIVE이고 손패가 빈 플레이어만 반환된다")
        void only_active_empty_hand_wins() {
            RoundState state = buildState(Map.of(
                    "Alice", buildPlayer(PlayerStatus.ACTIVE, 0),
                    "Bob",   buildPlayer(PlayerStatus.ACTIVE, 3),
                    "Carol", buildPlayer(PlayerStatus.ELIMINATED, 0)
            ), RoundEndCondition.GOING_OUT);

            List<String> winners = roundService.determineWinners(state);

            assertThat(winners).containsExactly("Alice");
        }

        @Test
        @DisplayName("ELIMINATED 플레이어가 손패가 비어도 승자에 포함되지 않는다")
        void eliminated_player_not_in_winners() {
            RoundState state = buildState(Map.of(
                    "Alice", buildPlayer(PlayerStatus.ELIMINATED, 0),
                    "Bob",   buildPlayer(PlayerStatus.ACTIVE, 0)
            ), RoundEndCondition.GOING_OUT);

            List<String> winners = roundService.determineWinners(state);

            assertThat(winners).containsExactly("Bob");
        }
    }

    // ----------------------------------------------------------------
    // findActiveWinners — BANKRUPTCY
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("BANKRUPTCY: ACTIVE 플레이어 전원 승자")
    class FindActiveWinners {

        @Test
        @DisplayName("ACTIVE 플레이어 전원이 승자로 반환된다")
        void all_active_players_win() {
            RoundState state = buildState(Map.of(
                    "Alice", buildPlayer(PlayerStatus.ACTIVE, 2),
                    "Bob",   buildPlayer(PlayerStatus.ACTIVE, 5),
                    "Carol", buildPlayer(PlayerStatus.ELIMINATED, 0)
            ), RoundEndCondition.BANKRUPTCY);

            List<String> winners = roundService.determineWinners(state);

            assertThat(winners).containsExactlyInAnyOrder("Alice", "Bob");
        }
    }

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private PlayerRoundState buildPlayer(PlayerStatus status, int handSize) {
        PlayerRoundState p = new PlayerRoundState();
        p.setStatus(status);
        p.setHand(handSize > 0
                ? List.of(new org.doubt.dto.Card(org.doubt.constant.Suit.SPADE, org.doubt.constant.Rank.ACE))
                : List.of());
        if (handSize > 1) {
            // 여러 장이 필요한 경우를 위한 간단한 처리
            p.setHand(java.util.Collections.nCopies(handSize,
                    new org.doubt.dto.Card(org.doubt.constant.Suit.SPADE, org.doubt.constant.Rank.ACE)));
        }
        return p;
    }

    private RoundState buildState(Map<String, PlayerRoundState> playerStates,
                                  RoundEndCondition endCondition) {
        RoundState state = new RoundState();
        state.setPlayerStates(playerStates);
        state.setEndCondition(endCondition);
        return state;
    }
}
