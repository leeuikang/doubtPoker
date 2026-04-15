package org.doubt.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessage {
    private String roomId;
    private String sender;
    private String message;
    private ChatMessage.MessageType type;

    public enum MessageType {
        ENTER, TALK, LEAVE
    }
}
