package org.doubt.dto;

import lombok.Getter;
import lombok.Setter;

import java.awt.*;

@Getter
@Setter
public class ChatMessage {
    private String roomId;
    private String sender;
    private String message;
    private TrayIcon.MessageType type;

    public enum MessageType {
        ENTER, TALK, LEAVE
    }
}
