package org.doubt.dto;

public record PokerPlayer(String sessionId, String name, int chips, boolean isReady) {
}
