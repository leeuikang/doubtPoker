package org.doubt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.doubt.constant.DrawSource;
import org.doubt.constant.ErrorCode;
import org.doubt.constant.GameAction;
import org.doubt.constant.GameStatus;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.Rank;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.Suit;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.GameMessage;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.PokerRoom;
import org.doubt.dto.RoundState;
import org.doubt.dto.request.DrawRequest;
import org.doubt.exception.GameException;
import org.doubt.handler.SessionManager;
import org.doubt.repository.PokerRoomRepository;
import org.doubt.service.RoundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GameController unit tests
 * - processJoinRoom(): session nickname auth (H-2)
 * - processAction()  : turn action routing, error guards, round-end broadcast
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameController")
class GameControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private RoundService roundService;

    @Mock
    private org.doubt.service.TournamentService tournamentService;

    @Mock
    private PokerRoomRepository pokerRoomRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GameController gameController;

    // ----------------------------------------------------------------
    // Session accessor helpers
    // ----------------------------------------------------------------

    private SimpMessageHeaderAccessor headerAccessorWithRoomAndNickname(String roomId, String nickname) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        if (roomId != null) attrs.put("roomId", roomId);
        if (nickname != null) attrs.put("nickname", nickname);
        when(accessor.getSessionAttributes()).thenReturn(attrs);
        return accessor;
    }

    private SimpMessageHeaderAccessor headerAccessorEmpty() {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        when(accessor.getSessionAttributes()).thenReturn(new HashMap<>());
        return accessor;
    }

    // ----------------------------------------------------------------
    // RoundState / PokerRoom helpers
    // ----------------------------------------------------------------

    private RoundState buildActiveState() {
        RoundState state = new RoundState();
        state.setTurnPhase(TurnPhase.DRAW);
        state.setEndCondition(null);
        state.setStockPile(new ArrayList<>());
        state.setDiscardPile(new ArrayList<>());
        state.setTableMelds(new ArrayList<>());

        PlayerRoundState ps = new PlayerRoundState();
        ps.setPlayerId("player1");
        ps.setHand(new ArrayList<>(List.of(new Card(Suit.SPADE, Rank.ACE))));
        ps.setStatus(PlayerStatus.ACTIVE);

        Map<String, PlayerRoundState> playerStates = new HashMap<>();
        playerStates.put("player1", ps);
        state.setPlayerStates(playerStates);
        state.setTurnOrder(List.of("player1"));
        state.setCurrentPlayerIndex(0);
        return state;
    }

    private PokerRoom buildRoom(String roomId, RoundState roundState) {
        PokerRoom room = new PokerRoom(roomId, "Test Room");
        room.setRoundState(roundState);
        room.setStatus(GameStatus.IN_PROGRESS);
        return room;
    }

    // ----------------------------------------------------------------
    // processJoinRoom
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("processJoinRoom")
    class ProcessJoinRoom {

        private SimpMessageHeaderAccessor headerAccessorWithNickname(String nickname) {
            SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
            Map<String, Object> attrs = new HashMap<>();
            if (nickname != null) {
                attrs.put("nickname", nickname);
            }
            when(accessor.getSessionAttributes()).thenReturn(attrs);
            return accessor;
        }

        @Test
        @DisplayName("Nickname present — sessionManager.addUserToRoom called with session nickname, not message sender")
        void session_nickname_is_used_for_addUserToRoom_not_message_sender() {
            String sessionNickname = "검증된닉네임";
            String messageSender = "사칭자";
            String roomId = "room-1";
            GameMessage message = new GameMessage("JOIN", roomId, messageSender, null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithNickname(sessionNickname);

            gameController.processJoinRoom(message, accessor);

            verify(sessionManager, times(1)).addUserToRoom(roomId, sessionNickname);
            verify(sessionManager, never()).addUserToRoom(roomId, messageSender);
        }

        @Test
        @DisplayName("Broadcast GameMessage sender is replaced with session nickname")
        void broadcast_sender_is_replaced_with_session_nickname() {
            String sessionNickname = "검증된닉네임";
            String messageSender = "사칭자";
            String roomId = "room-1";
            GameMessage message = new GameMessage("JOIN", roomId, messageSender, null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithNickname(sessionNickname);

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);

            gameController.processJoinRoom(message, accessor);

            verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId), captor.capture());
            GameMessage broadcast = captor.getValue();
            assertThat(broadcast.sender()).isEqualTo(sessionNickname);
            assertThat(broadcast.sender()).isNotEqualTo(messageSender);
        }

        @Test
        @DisplayName("No nickname in session — UNAUTHORIZED exception, no side effects")
        void null_nickname_in_session_throws_unauthorized() {
            GameMessage message = new GameMessage("JOIN", "room-1", "anyone", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithNickname(null);

            assertThatThrownBy(() -> gameController.processJoinRoom(message, accessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(sessionManager, never()).addUserToRoom(any(), any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
        }

        @Test
        @DisplayName("After join, session attrs contain roomId and userName")
        void session_attrs_are_updated_with_roomId_and_userName() {
            String sessionNickname = "홍길동";
            String roomId = "room-99";
            GameMessage message = new GameMessage("JOIN", roomId, "imposter", null);

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", sessionNickname);
            SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
            when(accessor.getSessionAttributes()).thenReturn(attrs);

            gameController.processJoinRoom(message, accessor);

            assertThat(attrs.get("roomId")).isEqualTo(roomId);
            assertThat(attrs.get("userName")).isEqualTo(sessionNickname);
        }

        @Test
        @DisplayName("UNAUTHORIZED exception message is '인증이 필요합니다.'")
        void unauthorized_exception_message_is_correct() {
            GameMessage message = new GameMessage("JOIN", "room-1", "anyone", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithNickname(null);

            assertThatThrownBy(() -> gameController.processJoinRoom(message, accessor))
                    .isInstanceOf(GameException.class)
                    .hasMessage("인증이 필요합니다.");
        }
    }

    // ----------------------------------------------------------------
    // processAction
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("processAction")
    class ProcessAction {

        private final String ROOM_ID = "room-action";
        private final String NICKNAME = "player1";

        /** Accessor with session roomId == message roomId and nickname set. */
        private SimpMessageHeaderAccessor validAccessor() {
            return headerAccessorWithRoomAndNickname(ROOM_ID, NICKNAME);
        }

        @Test
        @DisplayName("No nickname in session — UNAUTHORIZED exception")
        void throwsUnauthorizedWhenNoNickname() {
            GameMessage message = new GameMessage("DRAW", ROOM_ID, NICKNAME, null);
            SimpMessageHeaderAccessor accessor = headerAccessorEmpty();

            assertThatThrownBy(() -> gameController.processAction(message, accessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @Test
        @DisplayName("Session roomId does not match message roomId — NOT_IN_ROOM exception")
        void throwsNotInRoomWhenRoomIdMismatch() {
            GameMessage message = new GameMessage("DRAW", "other-room", NICKNAME, null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithRoomAndNickname(ROOM_ID, NICKNAME);

            assertThatThrownBy(() -> gameController.processAction(message, accessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_IN_ROOM));
        }

        @Test
        @DisplayName("Room not found in repository — ROOM_NOT_FOUND exception")
        void throwsRoomNotFound() {
            // parseAction succeeds (DRAW is valid), then findRoom is called
            GameMessage message = new GameMessage("DRAW", ROOM_ID, NICKNAME, null);
            when(pokerRoomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gameController.processAction(message, validAccessor()))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ROOM_NOT_FOUND));
        }

        @Test
        @DisplayName("Room has null roundState — INVALID_TURN_PHASE exception")
        void throwsInvalidTurnPhaseWhenRoundStateNull() {
            PokerRoom room = buildRoom(ROOM_ID, null);
            when(pokerRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            GameMessage message = new GameMessage("DRAW", ROOM_ID, NICKNAME, null);

            assertThatThrownBy(() -> gameController.processAction(message, validAccessor()))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("Round already ended (endCondition != null) — INVALID_TURN_PHASE exception")
        void throwsInvalidTurnPhaseWhenRoundAlreadyEnded() {
            RoundState endedState = buildActiveState();
            endedState.setEndCondition(RoundEndCondition.GOING_OUT);
            PokerRoom room = buildRoom(ROOM_ID, endedState);
            when(pokerRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            GameMessage message = new GameMessage("DRAW", ROOM_ID, NICKNAME, null);

            assertThatThrownBy(() -> gameController.processAction(message, validAccessor()))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("GameAction.READY is not a turn action — INVALID_TURN_PHASE exception")
        void throwsInvalidTurnPhaseForReadyAction() {
            // READY is a valid GameAction enum value, so parseAction succeeds,
            // but route() falls through to the default case and throws.
            RoundState activeState = buildActiveState();
            PokerRoom room = buildRoom(ROOM_ID, activeState);
            when(pokerRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            GameMessage message = new GameMessage(GameAction.READY.name(), ROOM_ID, NICKNAME, null);

            assertThatThrownBy(() -> gameController.processAction(message, validAccessor()))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("Unknown action type string — INVALID_TURN_PHASE exception (parseAction fails before findRoom)")
        void throwsInvalidTurnPhaseForUnknownActionType() {
            // "UNKNOWN_ACTION" is not a GameAction enum constant, so parseAction()
            // throws immediately — pokerRoomRepository is never consulted.
            GameMessage message = new GameMessage("UNKNOWN_ACTION", ROOM_ID, NICKNAME, null);

            assertThatThrownBy(() -> gameController.processAction(message, validAccessor()))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_TURN_PHASE));
        }

        @Test
        @DisplayName("DRAW action — handleDraw called, updated state broadcast to /topic/room/{roomId}")
        void routesDrawActionToRoundService() {
            RoundState activeState = buildActiveState();
            PokerRoom room = buildRoom(ROOM_ID, activeState);
            when(pokerRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            RoundState updatedState = buildActiveState();
            updatedState.setTurnPhase(TurnPhase.ACTION);

            when(objectMapper.convertValue(any(), eq(DrawRequest.class)))
                    .thenReturn(new DrawRequest(DrawSource.STOCK));
            when(roundService.handleDraw(eq(activeState), eq(NICKNAME), any(DrawRequest.class)))
                    .thenReturn(updatedState);

            GameMessage message = new GameMessage(GameAction.DRAW.name(), ROOM_ID, NICKNAME, null);

            gameController.processAction(message, validAccessor());

            verify(roundService, times(1)).handleDraw(eq(activeState), eq(NICKNAME), any(DrawRequest.class));

            ArgumentCaptor<GameMessage> broadcastCaptor = ArgumentCaptor.forClass(GameMessage.class);
            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/room/" + ROOM_ID), broadcastCaptor.capture());

            GameMessage broadcast = broadcastCaptor.getValue();
            assertThat(broadcast.roomId()).isEqualTo(ROOM_ID);
            assertThat(broadcast.sender()).isEqualTo(NICKNAME);
            assertThat(broadcast.payload()).isSameAs(updatedState);
        }

        @Test
        @DisplayName("After action, endCondition is set — resolveRound called and ROUND_END broadcast sent")
        void broadcastsRoundEndWhenConditionSet() {
            RoundState activeState = buildActiveState();
            PokerRoom room = buildRoom(ROOM_ID, activeState);
            when(pokerRoomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

            // Updated state with endCondition set (round ends after the action)
            RoundState endedState = buildActiveState();
            endedState.setEndCondition(RoundEndCondition.GOING_OUT);

            when(objectMapper.convertValue(any(), eq(DrawRequest.class)))
                    .thenReturn(new DrawRequest(DrawSource.STOCK));
            when(roundService.handleDraw(eq(activeState), eq(NICKNAME), any(DrawRequest.class)))
                    .thenReturn(endedState);

            Map<String, Integer> scoreDelta = Map.of(NICKNAME, 5);
            when(roundService.determineWinners(endedState)).thenReturn(List.of(NICKNAME));
            when(roundService.resolveRound(endedState)).thenReturn(scoreDelta);

            GameMessage message = new GameMessage(GameAction.DRAW.name(), ROOM_ID, NICKNAME, null);

            gameController.processAction(message, validAccessor());

            // resolveRound must be called exactly once with the ended state
            verify(roundService, times(1)).resolveRound(endedState);

            // Room status updated to ROUND_END
            assertThat(room.getStatus()).isEqualTo(GameStatus.ROUND_END);

            // Two broadcasts: one for the action result, one for ROUND_END
            ArgumentCaptor<GameMessage> broadcastCaptor = ArgumentCaptor.forClass(GameMessage.class);
            verify(messagingTemplate, times(2))
                    .convertAndSend(eq("/topic/room/" + ROOM_ID), broadcastCaptor.capture());

            List<GameMessage> broadcasts = broadcastCaptor.getAllValues();
            GameMessage roundEndBroadcast = broadcasts.get(1);
            assertThat(roundEndBroadcast.type()).isEqualTo("ROUND_END");
            assertThat(roundEndBroadcast.sender()).isEqualTo("SYSTEM");
            assertThat(roundEndBroadcast.payload()).isSameAs(scoreDelta);
        }
    }
}
