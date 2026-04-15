package org.doubt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.GameStatus;
import org.doubt.dto.PokerRoom;
import org.doubt.repository.PokerRoomRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomManagerService {

    private final PokerRoomRepository pokerRoomRepository;

    @Scheduled(fixedDelay = 60_000L)
    public void cleanupInactiveRooms(){

        log.info("Cleaning up inactive rooms");

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        pokerRoomRepository.findAll().forEach(room -> {
            if (room.getPlayerList().isEmpty() || room.getLastActivityTime().isBefore(threshold)) {
                // GL-C6: synchronized 내에서 상태·조건 재검증 — 삭제 직전 게임이 시작된 경우 방지
                synchronized (room) {
                    if (room.getStatus() == GameStatus.IN_PROGRESS
                            || room.getStatus() == GameStatus.ROUND_END) {
                        log.debug("Skipping active room: {}", room.getRoomId());
                        return;
                    }
                    // isEmpty/threshold 조건을 synchronized 블록 내에서 재검증
                    // (외부 isEmpty || isBefore 조건의 드모르간 부정: 플레이어 있고 최근 활동 → 유지)
                    if (!room.getPlayerList().isEmpty()
                            && !room.getLastActivityTime().isBefore(threshold)) {
                        return;
                    }
                    log.info("Deleting room: {}", room.getRoomId());
                    pokerRoomRepository.deleteById(room.getRoomId());
                }
            }
        });
    }
}
