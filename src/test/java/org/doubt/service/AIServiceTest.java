package org.doubt.service;

import org.doubt.constant.DrawSource;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.Rank;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Suit;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.doubt.dto.request.DiscardRequest;
import org.doubt.dto.request.DrawRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AIService")
class AIServiceTest {

    @Mock
    private RoundService roundService;

    @InjectMocks
    private AIService aiService;

    private static final String AI_PLAYER_ID = "aiPlayer";

    // ----------------------------------------------------------------
    // Helper methods
    // ----------------------------------------------------------------

    /** Build a minimal RoundState with given phase and player hand. */
    private RoundState buildState(TurnPhase phase, List<Card> hand) {
        RoundState state = new RoundState();
        state.setTurnPhase(phase);
        state.setEndCondition(null);
        state.setStockPile(new ArrayList<>());
        state.setDiscardPile(new ArrayList<>());
        state.setTableMelds(new ArrayList<>());

        PlayerRoundState playerState = new PlayerRoundState();
        playerState.setPlayerId(AI_PLAYER_ID);
        playerState.setHand(new ArrayList<>(hand));
        playerState.setStatus(PlayerStatus.ACTIVE);

        Map<String, PlayerRoundState> playerStates = new HashMap<>();
        playerStates.put(AI_PLAYER_ID, playerState);
        state.setPlayerStates(playerStates);

        state.setTurnOrder(List.of(AI_PLAYER_ID));
        state.setCurrentPlayerIndex(0);
        return state;
    }

    /** Build a PlayerRoundState with given status and hand. */
    private PlayerRoundState buildPlayer(PlayerStatus status, List<Card> hand) {
        PlayerRoundState player = new PlayerRoundState();
        player.setPlayerId(AI_PLAYER_ID);
        player.setStatus(status);
        player.setHand(new ArrayList<>(hand));
        return player;
    }

    // ----------------------------------------------------------------
    // Test cases
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("takeTurn")
    class TakeTurn {

        @Test
        @DisplayName("Round already ended — returns state unchanged, no RoundService calls")
        void skipWhenRoundEnded() {
            RoundState state = buildState(TurnPhase.DRAW, List.of(new Card(Suit.SPADE, Rank.ACE)));
            state.setEndCondition(RoundEndCondition.GOING_OUT);

            PlayerRoundState player = buildPlayer(PlayerStatus.ACTIVE, state.getPlayerStates()
                    .get(AI_PLAYER_ID).getHand());

            RoundState result = aiService.takeTurn(state, player);

            assertThat(result).isSameAs(state);
            verify(roundService, never()).handleDraw(any(), any(), any());
            verify(roundService, never()).handleDiscard(any(), any(), any());
        }

        @Test
        @DisplayName("Player is ELIMINATED — returns state unchanged, no RoundService calls")
        void skipWhenPlayerEliminated() {
            RoundState state = buildState(TurnPhase.DRAW, List.of(new Card(Suit.HEART, Rank.KING)));
            PlayerRoundState player = buildPlayer(PlayerStatus.ELIMINATED, List.of(new Card(Suit.HEART, Rank.KING)));

            RoundState result = aiService.takeTurn(state, player);

            assertThat(result).isSameAs(state);
            verify(roundService, never()).handleDraw(any(), any(), any());
            verify(roundService, never()).handleDiscard(any(), any(), any());
        }

        @Test
        @DisplayName("DRAW phase — draws from STOCK then discards the highest non-SEVEN card (KING)")
        void drawFromStockThenDiscardHighest() {
            // Hand: KING(13), SEVEN(7), ACE(1)
            Card king = new Card(Suit.SPADE, Rank.KING);
            Card seven = new Card(Suit.CLUB, Rank.SEVEN);
            Card ace = new Card(Suit.HEART, Rank.ACE);
            List<Card> hand = List.of(king, seven, ace);

            RoundState initialState = buildState(TurnPhase.DRAW, hand);

            // State after draw: same hand + one more card, now in ACTION phase
            RoundState stateAfterDraw = buildState(TurnPhase.ACTION, new ArrayList<>(hand));
            stateAfterDraw.getPlayerStates().get(AI_PLAYER_ID).setHand(new ArrayList<>(hand));

            RoundState stateAfterDiscard = buildState(TurnPhase.DRAW, List.of(seven, ace));

            ArgumentCaptor<DrawRequest> drawCaptor = ArgumentCaptor.forClass(DrawRequest.class);
            ArgumentCaptor<DiscardRequest> discardCaptor = ArgumentCaptor.forClass(DiscardRequest.class);

            when(roundService.handleDraw(eq(initialState), eq(AI_PLAYER_ID), drawCaptor.capture()))
                    .thenReturn(stateAfterDraw);
            when(roundService.handleDiscard(eq(stateAfterDraw), eq(AI_PLAYER_ID), discardCaptor.capture()))
                    .thenReturn(stateAfterDiscard);

            RoundState result = aiService.takeTurn(initialState, buildPlayer(PlayerStatus.ACTIVE, hand));

            assertThat(result).isSameAs(stateAfterDiscard);

            // Draw source must be STOCK
            assertThat(drawCaptor.getValue().source()).isEqualTo(DrawSource.STOCK);

            // Discarded card must be KING (highest base point, non-SEVEN)
            assertThat(discardCaptor.getValue().card()).isEqualTo(king);
        }

        @Test
        @DisplayName("ACTION phase — discards the highest non-SEVEN card (KING) from [THREE, KING, SEVEN]")
        void discardHighestNonSevenCard() {
            Card three = new Card(Suit.DIAMOND, Rank.THREE);
            Card king = new Card(Suit.SPADE, Rank.KING);
            Card seven = new Card(Suit.HEART, Rank.SEVEN);
            List<Card> hand = List.of(three, king, seven);

            RoundState state = buildState(TurnPhase.ACTION, hand);
            RoundState stateAfterDiscard = buildState(TurnPhase.DRAW, List.of(three, seven));

            ArgumentCaptor<DiscardRequest> discardCaptor = ArgumentCaptor.forClass(DiscardRequest.class);
            when(roundService.handleDiscard(eq(state), eq(AI_PLAYER_ID), discardCaptor.capture()))
                    .thenReturn(stateAfterDiscard);

            RoundState result = aiService.takeTurn(state, buildPlayer(PlayerStatus.ACTIVE, hand));

            assertThat(result).isSameAs(stateAfterDiscard);
            verify(roundService, never()).handleDraw(any(), any(), any());
            assertThat(discardCaptor.getValue().card()).isEqualTo(king);
        }

        @Test
        @DisplayName("ACTION phase, all SEVENS — discards the first card in hand")
        void discardSevenWhenAllSevens() {
            Card seven1 = new Card(Suit.SPADE, Rank.SEVEN);
            Card seven2 = new Card(Suit.HEART, Rank.SEVEN);
            Card seven3 = new Card(Suit.DIAMOND, Rank.SEVEN);
            List<Card> hand = List.of(seven1, seven2, seven3);

            RoundState state = buildState(TurnPhase.ACTION, hand);
            RoundState stateAfterDiscard = buildState(TurnPhase.DRAW, List.of(seven2, seven3));

            ArgumentCaptor<DiscardRequest> discardCaptor = ArgumentCaptor.forClass(DiscardRequest.class);
            when(roundService.handleDiscard(eq(state), eq(AI_PLAYER_ID), discardCaptor.capture()))
                    .thenReturn(stateAfterDiscard);

            RoundState result = aiService.takeTurn(state, buildPlayer(PlayerStatus.ACTIVE, hand));

            assertThat(result).isSameAs(stateAfterDiscard);
            // When all are SEVEN, the first card in the list is discarded
            assertThat(discardCaptor.getValue().card()).isEqualTo(seven1);
        }

        @Test
        @DisplayName("Round ends after draw (e.g. BANKRUPTCY) — handleDiscard is NOT called")
        void returnEarlyIfRoundEndsAfterDraw() {
            Card king = new Card(Suit.CLUB, Rank.KING);
            List<Card> hand = List.of(king);

            RoundState initialState = buildState(TurnPhase.DRAW, hand);

            // After draw the round ends immediately
            RoundState stateAfterDraw = buildState(TurnPhase.DRAW, hand);
            stateAfterDraw.setEndCondition(RoundEndCondition.BANKRUPTCY);

            when(roundService.handleDraw(eq(initialState), eq(AI_PLAYER_ID), any(DrawRequest.class)))
                    .thenReturn(stateAfterDraw);

            RoundState result = aiService.takeTurn(initialState, buildPlayer(PlayerStatus.ACTIVE, hand));

            assertThat(result).isSameAs(stateAfterDraw);
            assertThat(result.getEndCondition()).isEqualTo(RoundEndCondition.BANKRUPTCY);
            verify(roundService, never()).handleDiscard(any(), any(), any());
        }

        @Test
        @DisplayName("Player is DISCONNECTED — returns state unchanged, no RoundService calls")
        void skipWhenPlayerDisconnected() {
            RoundState state = buildState(TurnPhase.ACTION, List.of(new Card(Suit.SPADE, Rank.TEN)));
            PlayerRoundState player = buildPlayer(PlayerStatus.DISCONNECTED,
                    List.of(new Card(Suit.SPADE, Rank.TEN)));

            RoundState result = aiService.takeTurn(state, player);

            assertThat(result).isSameAs(state);
            verify(roundService, never()).handleDraw(any(), any(), any());
            verify(roundService, never()).handleDiscard(any(), any(), any());
        }
    }
}
