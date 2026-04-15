package org.doubt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.doubt.constant.ErrorCode;
import org.doubt.constant.GameStatus;
import org.doubt.dto.GameMessage;
import org.doubt.dto.PokerPlayer;
import org.doubt.dto.PokerRoom;
import org.doubt.dto.RoundState;
import org.doubt.dto.TournamentState;
import org.doubt.exception.GameException;
import org.doubt.handler.SessionManager;
import org.doubt.repository.PokerRoomRepository;
import org.doubt.service.GameBroadcastService;
import org.doubt.service.RoundService;
import org.doubt.service.TournamentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SEC-M4 보안 취약점 수정 검증 테스트
 *
 * <p>원래 취약점(SEC-M4): processStartRound() 에 방장 확인이 없어
 * 방 안의 모든 플레이어가 라운드를 시작할 수 있었다.</p>
 *
 * <p>해결 방식:
 * <ul>
 *   <li>PokerRoom 에 hostId 필드 추가</li>
 *   <li>processJoinRoom() 에서 첫 입장 플레이어를 방장으로 지정</li>
 *   <li>processStartRound() 에서 방장 여부 확인 — 아니면 NOT_HOST 예외</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SEC-M4 수정 검증: 방장만 라운드를 시작할 수 있다")
class SecM4HostCheckTest {

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

    private SimpMessageHeaderAccessor accessorWithNickname(String nickname) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        if (nickname != null) attrs.put("nickname", nickname);
        when(accessor.getSessionAttributes()).thenReturn(attrs);
        return accessor;
    }

    private SimpMessageHeaderAccessor accessorWithNicknameAndRoom(String nickname, String roomId) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        if (nickname != null) attrs.put("nickname", nickname);
        if (roomId != null) attrs.put("roomId", roomId);
        when(accessor.getSessionAttributes()).thenReturn(attrs);
        return accessor;
    }

    /**
     * PokerPlayer 는 @Getter 만 있으므로 ReflectionTestUtils 로 name 필드를 주입한다.
     */
    private PokerPlayer buildPlayer(String name) {
        PokerPlayer player = new PokerPlayer();
        ReflectionTestUtils.setField(player, "name", name);
        return player;
    }

    // ----------------------------------------------------------------
    // PokerRoom hostId 기본 동작
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("PokerRoom hostId 필드")
    class PokerRoomHostId {

        @Test
        @DisplayName("새로 생성된 PokerRoom 의 hostId 는 null 이다")
        void hostId_is_null_initially() {
            PokerRoom room = new PokerRoom("r1", "room");

            assertThat(room.getHostId()).isNull();
        }

        @Test
        @DisplayName("setHostId 호출 후 getHostId 는 설정한 값을 반환한다")
        void setHostId_assigns_correctly() {
            PokerRoom room = new PokerRoom("r1", "room");

            room.setHostId("Alice");

            assertThat(room.getHostId()).isEqualTo("Alice");
        }
    }

    // ----------------------------------------------------------------
    // processJoinRoom — 방장 지정
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("processJoinRoom — 방장 지정")
    class ProcessJoinRoom_HostAssignment {

        @Test
        @DisplayName("첫 번째 입장 플레이어가 방장으로 지정된다")
        void first_join_sets_hostId() {
            String roomId = "room-host";
            String nickname = "Alice";
            PokerRoom room = new PokerRoom(roomId, "테스트방");
            // hostId == null 상태 (초기)

            GameMessage message = new GameMessage("JOIN", roomId, nickname, null);
            SimpMessageHeaderAccessor accessor = accessorWithNickname(nickname);

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

            gameController.processJoinRoom(message, accessor);

            assertThat(room.getHostId()).isEqualTo(nickname);
        }

        @Test
        @DisplayName("방장이 이미 지정된 방에 두 번째 플레이어가 입장해도 방장은 바뀌지 않는다")
        void second_join_does_not_change_hostId() {
            String roomId = "room-host2";
            PokerRoom room = new PokerRoom(roomId, "테스트방");
            room.setHostId("Alice");  // 방장 선지정

            GameMessage message = new GameMessage("JOIN", roomId, "Bob", null);
            SimpMessageHeaderAccessor accessor = accessorWithNickname("Bob");

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

            gameController.processJoinRoom(message, accessor);

            assertThat(room.getHostId()).isEqualTo("Alice");
        }
    }

    // ----------------------------------------------------------------
    // processStartRound — 방장 체크
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("processStartRound — 방장 체크")
    class ProcessStartRound_HostCheck {

        private PokerRoom buildRoomWithHost(String roomId, String hostId) {
            PokerRoom room = new PokerRoom(roomId, "테스트방");
            room.setHostId(hostId);
            room.setStatus(GameStatus.WAITING);

            List<PokerPlayer> players = new ArrayList<>();
            players.add(buildPlayer("Alice"));
            players.add(buildPlayer("Bob"));
            room.setPlayerList(players);

            return room;
        }

        @Test
        @DisplayName("방장이 아닌 플레이어가 라운드 시작 시 NOT_HOST 예외가 발생한다")
        void non_host_cannot_start_round() {
            String roomId = "room-sec-m4";
            PokerRoom room = buildRoomWithHost(roomId, "Alice");

            GameMessage message = new GameMessage("START_ROUND", roomId, "Bob", null);
            SimpMessageHeaderAccessor accessor = accessorWithNicknameAndRoom("Bob", roomId);

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

            assertThatThrownBy(() -> gameController.processStartRound(message, accessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex ->
                            assertThat(((GameException) ex).getErrorCode())
                                    .isEqualTo(ErrorCode.NOT_HOST));
        }

        @Test
        @DisplayName("방장이 라운드 시작 시 NOT_HOST 예외가 발생하지 않는다")
        void host_can_start_round() {
            String roomId = "room-sec-m4-host";
            PokerRoom room = buildRoomWithHost(roomId, "Alice");

            // tournamentService.initTournament stub
            TournamentState tournament = new TournamentState(roomId);
            tournament.setCurrentRound(1);
            Map<String, Integer> scores = new ConcurrentHashMap<>();
            scores.put("Alice", 100);
            scores.put("Bob", 100);
            tournament.setScores(scores);
            tournament.setEliminatedPlayers(new HashSet<>());
            tournament.setRoundHistory(new ArrayList<>());

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
            when(tournamentService.initTournament(anyString(), anyList()))
                    .thenReturn(tournament);

            // roundService.startRound stub
            RoundState roundState = new RoundState();
            roundState.setTurnOrder(List.of("Alice", "Bob"));
            roundState.setCurrentPlayerIndex(0);
            roundState.setStockPile(new ArrayList<>());
            roundState.setDiscardPile(new ArrayList<>());
            roundState.setTableMelds(new ArrayList<>());
            roundState.setPlayerStates(new HashMap<>());
            when(roundService.startRound(anyString(), anyList(), anyString()))
                    .thenReturn(roundState);

            GameMessage message = new GameMessage("START_ROUND", roomId, "Alice", null);
            SimpMessageHeaderAccessor accessor = accessorWithNicknameAndRoom("Alice", roomId);

            // NOT_HOST 예외 없이 정상 실행되어야 한다
            assertThatCode(() -> gameController.processStartRound(message, accessor))
                    .doesNotThrowAnyException();
        }
    }
}
