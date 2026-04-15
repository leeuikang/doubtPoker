package org.doubt.service;

import org.doubt.constant.GameStatus;
import org.doubt.dto.PokerRoom;
import org.doubt.repository.PokerRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GL-C6 버그 수정 검증 테스트
 * cleanupInactiveRooms 가 IN_PROGRESS·ROUND_END 상태의 방을 건너뛰는지 확인
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GL-C6: cleanupInactiveRooms — 진행 중 방 삭제 방지")
class GlC6FixTest {

    @Mock
    private PokerRoomRepository pokerRoomRepository;

    @InjectMocks
    private RoomManagerService roomManagerService;

    /**
     * playerList 가 비어 있고, lastActivityTime 이 기준 시간(10분) 이전인 방을 만드는 헬퍼.
     * Lombok @Setter 로 생성된 setLastActivityTime() 을 사용한다.
     */
    private PokerRoom buildOldEmptyRoom(String roomId, GameStatus status) {
        PokerRoom room = new PokerRoom(roomId, "테스트방-" + roomId);
        room.setStatus(status);
        // 15분 전 시각 설정 → cleanupInactiveRooms 임계값(10분) 초과
        room.setLastActivityTime(LocalDateTime.now().minusMinutes(15));
        return room;
    }

    @Nested
    @DisplayName("IN_PROGRESS / ROUND_END 방 보호")
    class ActiveRoomProtection {

        @Test
        @DisplayName("IN_PROGRESS 상태이고 빈 방이라도 삭제하지 않는다")
        void cleanupInactiveRooms_skipsInProgressRoom() {
            PokerRoom room = buildOldEmptyRoom("room-ip", GameStatus.IN_PROGRESS);
            when(pokerRoomRepository.findAll()).thenReturn(List.of(room));

            roomManagerService.cleanupInactiveRooms();

            verify(pokerRoomRepository, never()).deleteById("room-ip");
        }

        @Test
        @DisplayName("ROUND_END 상태이고 빈 방이라도 삭제하지 않는다")
        void cleanupInactiveRooms_skipsRoundEndRoom() {
            PokerRoom room = buildOldEmptyRoom("room-re", GameStatus.ROUND_END);
            when(pokerRoomRepository.findAll()).thenReturn(List.of(room));

            roomManagerService.cleanupInactiveRooms();

            verify(pokerRoomRepository, never()).deleteById("room-re");
        }
    }

    @Nested
    @DisplayName("WAITING 방 삭제 조건")
    class WaitingRoomCleanup {

        @Test
        @DisplayName("WAITING 상태이고 빈 방에 마지막 활동이 10분 초과이면 삭제된다")
        void cleanupInactiveRooms_deletesWaitingRoom_whenInactive() {
            PokerRoom room = buildOldEmptyRoom("room-wait", GameStatus.WAITING);
            when(pokerRoomRepository.findAll()).thenReturn(List.of(room));

            roomManagerService.cleanupInactiveRooms();

            verify(pokerRoomRepository).deleteById("room-wait");
        }

        @Test
        @DisplayName("WAITING 상태에 플레이어가 없으면 활동 시간과 무관하게 삭제된다")
        void cleanupInactiveRooms_deletesWaitingRoom_whenPlayersEmpty() {
            // lastActivityTime 을 방금 전으로 두어 threshold 이내이지만
            // playerList.isEmpty() == true 이므로 외부 if 조건(||) 통과
            // synchronized 내부: status = WAITING → IN_PROGRESS/ROUND_END 가드 통과
            // 내부 재검증: !isEmpty() == false → return 없이 삭제 진행
            PokerRoom room = new PokerRoom("room-empty", "빈방");
            room.setStatus(GameStatus.WAITING);
            // lastActivityTime 은 생성자에서 LocalDateTime.now() 로 초기화 — 임계값 이내
            when(pokerRoomRepository.findAll()).thenReturn(List.of(room));

            roomManagerService.cleanupInactiveRooms();

            verify(pokerRoomRepository).deleteById("room-empty");
        }
    }
}
