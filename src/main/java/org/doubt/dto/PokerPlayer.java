package org.doubt.dto;

import lombok.Getter;

@Getter
public class PokerPlayer {
    String sessionId;
    String name;
    int chips;
    boolean isReady;
}
