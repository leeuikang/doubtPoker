package org.doubt.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
public class PokerPlayer {
    private String sessionId;
    private String name;
    private int chips;
    private boolean isReady;

    public PokerPlayer() {}

    @Builder
    public PokerPlayer(String sessionId, String name, int chips, boolean isReady) {
        this.sessionId = sessionId;
        this.name = name;
        this.chips = chips;
        this.isReady = isReady;
    }
}
