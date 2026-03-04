package org.doubt.dto;


import lombok.Getter;
import lombok.Setter;
import org.doubt.constant.GameStatus;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PokerRoom {
    private String roomId;
    private String roomName;
    private List<PokerPlayer> playerList;
    private GameStatus status;
    private int currentIndex;
    private int totalPot;

    public PokerRoom(String roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.playerList = new ArrayList<>();
        this.status = GameStatus.READY;
        this.currentIndex = 0;
        this.totalPot = 0;
    }
}
