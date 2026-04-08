package org.doubt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        pokerRoomRepository.findAll().forEach(room ->{
            if(room.getPlayerList().isEmpty() || room.getLastActivityTime().isBefore(threshold)){
                log.info("Deleting room: {}", room.getRoomId());
                pokerRoomRepository.deleteById(room.getRoomId());
            }
        });
    }
}
