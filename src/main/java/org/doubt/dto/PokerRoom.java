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
    private LocalDateTime lastActivityTime;
    private RoundState roundState;
    private TournamentState tournamentState;
    /** 방장 닉네임 — 첫 입장한 플레이어가 방장이 된다 (SEC-M4) */
    private String hostId;

    public PokerRoom(String roomId, String roomName) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.playerList = new ArrayList<>();
        this.status = GameStatus.WAITING;
        lastActivityTime = LocalDateTime.now();
    }

    public void updateActivityTime(){
        this.lastActivityTime = LocalDateTime.now();
    }
}
