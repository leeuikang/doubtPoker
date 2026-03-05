package org.doubt.repository;

import org.doubt.dto.PokerRoom;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class PokerRoomRepository {
    private final Map<String, PokerRoom> roomMap = new HashMap<>();

    public PokerRoom save(PokerRoom pokerRoom){
        roomMap.put(pokerRoom.getRoomId(), pokerRoom);
        return pokerRoom;
    }

    public Optional<PokerRoom> findById(String roomId){
        return Optional.ofNullable(roomMap.get(roomId));
    }

    public void deleteById(String roomId){
        roomMap.remove(roomId);
    }

    public List<PokerRoom> findAll(){
        return new ArrayList<>(roomMap.values());
    }
}
