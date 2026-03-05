package org.doubt.dto;

public record GameMessage(String type, String roomId, String sender, Object payload) {
}
