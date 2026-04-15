package org.doubt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.doubt.constant.GameStatus;
import org.doubt.constant.RoundEndCondition;
import org.doubt.dto.GameMessage;
import org.doubt.dto.PokerRoom;
import org.doubt.dto.RoundState;
import org.doubt.dto.TournamentState;
import org.doubt.handler.SessionManager;
import org.doubt.repository.PokerRoomRepository;
import org.doubt.service.GameBroadcastService;
import org.doubt.service.RoundService;
import org.doubt.service.TournamentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CQ-W6 코드 품질 수정 검증 테스트
 *
 * <p>원래 문제(CQ-W6): {@code GameController.processAction}이 90줄 이상으로 SRP를 위반했다.
 * 라운드 종료 후처리 로직이 메서드 내부에 인라인으로 포함되어 있었다.</p>
 *
 * <p>수정 후: 라운드 종료 후처리를 {@code finalizeRound(PokerRoom, RoundState, String)}
 * private 메서드로 분리. ROUND_END·TOURNAMENT_END 브로드캐스트가 올바른 경로로 전송되는지 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CQ-W6 수정 검증: finalizeRound 분리")
class CqW6FixTest {

    @Mock
    private GameBroadcastService gameBroadcastService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private RoundService roundService;

    @Mock
    private TournamentService tournamentService;

    @Mock
    private PokerRoomRepository pokerRoomRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GameController gameController;

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private SimpMessageHeaderAccessor accessorWithSession(String nickname, String roomId) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("nickname", nickname);
        attrs.put("roomId", roomId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);
        return accessor;
    }

    private PokerRoom buildRoom(String roomId) {
        PokerRoom room = new PokerRoom(roomId, "테스트방");
        room.setStatus(GameStatus.IN_PROGRESS);

        TournamentState tournament = new TournamentState(roomId);
        tournament.setCurrentRound(1);
        Map<String, Integer> scores = new ConcurrentHashMap<>();
        scores.put("Alice", 100);
        scores.put("Bob", 100);
        tournament.setScores(scores);
        tournament.setEliminatedPlayers(new HashSet<>());
        tournament.setRoundHistory(new ArrayList<>());
        room.setTournamentState(tournament);
        return room;
    }

    /** 진행 중인 라운드 상태(endCondition = null) — room에 설정할 초기 상태 */
    private RoundState buildOngoingRoundState() {
        RoundState state = new RoundState();
        state.setTurnOrder(List.of("Alice", "Bob"));
        state.setCurrentPlayerIndex(0);
        state.setEndCondition(null);
        state.setPlayerStates(new HashMap<>());
        state.setTableMelds(new ArrayList<>());
        return state;
    }

    /** handleStop stub이 반환할 종료 라운드 상태(endCondition 설정됨) */
    private RoundState buildFinishedRoundState() {
        RoundState state = new RoundState();
        state.setTurnOrder(List.of("Alice", "Bob"));
        state.setCurrentPlayerIndex(0);
        state.setEndCondition(RoundEndCondition.GOING_OUT);
        state.setPlayerStates(new HashMap<>());
        state.setTableMelds(new ArrayList<>());
        return state;
    }

    // ----------------------------------------------------------------
    // ROUND_END 브로드캐스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("라운드 종료 시 ROUND_END 브로드캐스트")
    class RoundEndBroadcast {

        @Test
        @DisplayName("라운드 종료 조건이 설정되면 ROUND_END 메시지가 브로드캐스트된다")
        void round_end_broadcasts_round_end_message() {
            String roomId = "room-cqw6-1";
            PokerRoom room = buildRoom(roomId);
            room.setRoundState(buildOngoingRoundState());
            RoundState finishedState = buildFinishedRoundState();

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
            // STOP 액션 → handleStop이 종료 상태 반환
            when(roundService.handleStop(any(), anyString(), any())).thenReturn(finishedState);
            when(roundService.determineWinners(any())).thenReturn(List.of("Alice"));
            when(roundService.resolveRound(any(), anyList())).thenReturn(Map.of("Alice", 10));

            TournamentState tournament = room.getTournamentState();
            when(tournamentService.applyRoundResult(any(), any(), anyList())).thenReturn(tournament);
            when(tournamentService.isFinished(any())).thenReturn(false);

            GameMessage message = new GameMessage("STOP", roomId, "Alice", null);
            SimpMessageHeaderAccessor accessor = accessorWithSession("Alice", roomId);

            gameController.processAction(message, accessor);

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);
            verify(gameBroadcastService).broadcast(anyString(), captor.capture());
            assertThat(captor.getValue().type()).isEqualTo("ROUND_END");
        }

        @Test
        @DisplayName("라운드가 종료되지 않으면 ROUND_END 브로드캐스트가 없다")
        void no_end_condition_no_round_end_broadcast() {
            String roomId = "room-cqw6-noend";
            PokerRoom room = buildRoom(roomId);

            RoundState ongoing = new RoundState();
            ongoing.setTurnOrder(List.of("Alice", "Bob"));
            ongoing.setCurrentPlayerIndex(0);
            ongoing.setEndCondition(null); // 종료 조건 없음
            ongoing.setPlayerStates(new HashMap<>());
            ongoing.setTableMelds(new ArrayList<>());
            room.setRoundState(ongoing);

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
            when(roundService.handleStop(any(), anyString(), any())).thenReturn(ongoing);

            GameMessage message = new GameMessage("STOP", roomId, "Alice", null);
            SimpMessageHeaderAccessor accessor = accessorWithSession("Alice", roomId);

            gameController.processAction(message, accessor);

            // ROUND_END broadcast 없음 — broadcastRoundState만 호출
            verify(gameBroadcastService, never()).broadcast(anyString(), any());
        }
    }

    // ----------------------------------------------------------------
    // TOURNAMENT_END 브로드캐스트
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("토너먼트 종료 시 TOURNAMENT_END 브로드캐스트")
    class TournamentEndBroadcast {

        @Test
        @DisplayName("토너먼트가 종료되면 ROUND_END + TOURNAMENT_END 두 번 브로드캐스트된다")
        void tournament_finished_broadcasts_both_messages() {
            String roomId = "room-cqw6-2";
            PokerRoom room = buildRoom(roomId);
            room.setRoundState(buildOngoingRoundState());
            RoundState finishedState = buildFinishedRoundState();

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
            when(roundService.handleStop(any(), anyString(), any())).thenReturn(finishedState);
            when(roundService.determineWinners(any())).thenReturn(List.of("Alice"));
            when(roundService.resolveRound(any(), anyList())).thenReturn(Map.of("Alice", 10));

            TournamentState tournament = room.getTournamentState();
            when(tournamentService.applyRoundResult(any(), any(), anyList())).thenReturn(tournament);
            when(tournamentService.isFinished(any())).thenReturn(true);
            when(tournamentService.getWinners(any())).thenReturn(List.of("Alice"));

            GameMessage message = new GameMessage("STOP", roomId, "Alice", null);
            SimpMessageHeaderAccessor accessor = accessorWithSession("Alice", roomId);

            gameController.processAction(message, accessor);

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);
            verify(gameBroadcastService, times(2)).broadcast(anyString(), captor.capture());
            List<String> types = captor.getAllValues().stream().map(GameMessage::type).toList();
            assertThat(types).containsExactlyInAnyOrder("ROUND_END", "TOURNAMENT_END");
        }

        @Test
        @DisplayName("토너먼트 종료 후 방 상태는 TOURNAMENT_END로 전환된다")
        void tournament_finished_sets_room_status() {
            String roomId = "room-cqw6-3";
            PokerRoom room = buildRoom(roomId);
            room.setRoundState(buildOngoingRoundState());
            RoundState finishedState = buildFinishedRoundState();

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
            when(roundService.handleStop(any(), anyString(), any())).thenReturn(finishedState);
            when(roundService.determineWinners(any())).thenReturn(List.of("Alice"));
            when(roundService.resolveRound(any(), anyList())).thenReturn(Map.of("Alice", 10));

            TournamentState tournament = room.getTournamentState();
            when(tournamentService.applyRoundResult(any(), any(), anyList())).thenReturn(tournament);
            when(tournamentService.isFinished(any())).thenReturn(true);
            when(tournamentService.getWinners(any())).thenReturn(List.of("Alice"));

            GameMessage message = new GameMessage("STOP", roomId, "Alice", null);
            SimpMessageHeaderAccessor accessor = accessorWithSession("Alice", roomId);

            gameController.processAction(message, accessor);

            assertThat(room.getStatus()).isEqualTo(GameStatus.TOURNAMENT_END);
        }
    }
}
