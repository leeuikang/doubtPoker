package org.doubt.dto;


import lombok.Getter;
import lombok.Setter;
import org.doubt.constant.GameStatus;

import java.time.LocalDateTime;
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
    private LocalDateTime lastActivityTime;
    private RoundState roundState;

    public PokerRoom(String roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.playerList = new ArrayList<>();
        this.status = GameStatus.WAITING;
        this.currentIndex = 0;
        this.totalPot = 0;
        lastActivityTime = LocalDateTime.now();
    }

    public void updateActivityTime(){
        this.lastActivityTime = LocalDateTime.now();
    }
}
