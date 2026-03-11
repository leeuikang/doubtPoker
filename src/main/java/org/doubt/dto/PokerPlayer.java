package org.doubt.dto;

import org.doubt.constant.ChipStatus;

import java.util.List;

public record PokerPlayer(String sessionId, String name, int chips, boolean isReady, List<PokerCard> hand) {
    public static PokerPlayer of(String sessionId, String name) {
        return new PokerPlayer(sessionId, name, ChipStatus.DEFAULT.getValue(), false, new java.util.ArrayList<>());
    }
}
