package org.doubt.service;

import org.doubt.constant.ErrorCode;
import org.doubt.constant.GameStatus;
import org.doubt.dto.PokerPlayer;
import org.doubt.dto.PokerRoom;
import org.doubt.exception.GameException;
import org.doubt.repository.PokerRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * GameService 단위 테스트
 * - startGame() 정상 케이스 및 예외 케이스 (L-2 null 안전성 포함)
 */
@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private PokerRoomRepository pokerRoomRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private GameService gameService;

    // --- 헬퍼: PokerPlayer에 sessionId, name 설정 ---

    private PokerPlayer buildPlayer(String sessionId, String name) throws Exception {
        PokerPlayer player = new PokerPlayer();
        setField(player, "sessionId", sessionId);
        setField(player, "name", name);
        return player;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // ==================== startGame ====================

    @Nested
    @DisplayName("startGame")
    class StartGame {

        @Test
        @DisplayName("방을 찾을 수 없으면 ROOM_NOT_FOUND 예외가 발생한다")
        void room_not_found_throws_exception() {
            when(pokerRoomRepository.findById("unknown-room")).thenReturn(Optional.empty());

            GameException ex = assertThrows(GameException.class,
                    () -> gameService.startGame("unknown-room"));

            assertEquals(ErrorCode.ROOM_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("플레이어가 없으면 NOT_ENOUGH_PLAYERS 예외가 발생한다 (L-2)")
        void empty_player_list_throws_not_enough_players() {
            PokerRoom emptyRoom = new PokerRoom("room-empty", "빈 방");
            // playerList는 기본값으로 빈 ArrayList이므로 플레이어를 추가하지 않는다

            when(pokerRoomRepository.findById("room-empty")).thenReturn(Optional.of(emptyRoom));

            GameException ex = assertThrows(GameException.class,
                    () -> gameService.startGame("room-empty"));

            assertEquals(ErrorCode.NOT_ENOUGH_PLAYERS, ex.getErrorCode(),
                    "플레이어 0명일 때 NOT_ENOUGH_PLAYERS가 발생해야 한다");
        }

        @Test
        @DisplayName("플레이어가 1명이면 NOT_ENOUGH_PLAYERS 예외가 발생한다 (ruleBook 제2조: 최소 2명)")
        void single_player_throws_not_enough_players() throws Exception {
            PokerRoom room = new PokerRoom("room-single", "1인 방");
            room.getPlayerList().add(buildPlayer("session-solo", "혼자"));

            when(pokerRoomRepository.findById("room-single")).thenReturn(Optional.of(room));

            GameException ex = assertThrows(GameException.class,
                    () -> gameService.startGame("room-single"));

            assertEquals(ErrorCode.NOT_ENOUGH_PLAYERS, ex.getErrorCode(),
                    "플레이어 1명일 때 NOT_ENOUGH_PLAYERS가 발생해야 한다");
        }

        @Test
        @DisplayName("플레이어가 있으면 게임이 정상 시작되고 상태가 IN_PROGRESS로 변경된다")
        void valid_room_with_players_starts_game() throws Exception {
            PokerRoom room = new PokerRoom("room-1", "정상 방");
            room.getPlayerList().add(buildPlayer("session-a", "플레이어A"));
            room.getPlayerList().add(buildPlayer("session-b", "플레이어B"));

            when(pokerRoomRepository.findById("room-1")).thenReturn(Optional.of(room));

            gameService.startGame("room-1");

            assertEquals(GameStatus.IN_PROGRESS, room.getStatus(),
                    "게임 시작 후 상태는 IN_PROGRESS여야 한다");
        }

        @Test
        @DisplayName("카드 52장이 플레이어들에게 균등하게 배분된다")
        void cards_are_distributed_to_all_players() throws Exception {
            PokerRoom room = new PokerRoom("room-2", "배분 테스트");
            room.getPlayerList().add(buildPlayer("session-a", "플레이어A"));
            room.getPlayerList().add(buildPlayer("session-b", "플레이어B"));

            when(pokerRoomRepository.findById("room-2")).thenReturn(Optional.of(room));

            gameService.startGame("room-2");

            int totalCards = room.getPlayerList().stream()
                    .mapToInt(p -> p.getHand().size())
                    .sum();

            assertEquals(52, totalCards, "전체 배분된 카드는 52장이어야 한다");
        }

        @Test
        @DisplayName("게임 시작 시 브로드캐스트 메시지가 1회 전송된다")
        void broadcast_is_called_once_on_start() throws Exception {
            PokerRoom room = new PokerRoom("room-3", "브로드캐스트 테스트");
            room.getPlayerList().add(buildPlayer("session-a", "플레이어A"));
            room.getPlayerList().add(buildPlayer("session-b", "플레이어B"));

            when(pokerRoomRepository.findById("room-3")).thenReturn(Optional.of(room));

            gameService.startGame("room-3");

            verify(messagingTemplate, times(1))
                    .convertAndSend(anyString(), any(Object.class));
        }

    }
}
