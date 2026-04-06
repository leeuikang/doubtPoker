package org.doubt.controller;

import lombok.RequiredArgsConstructor;
import org.doubt.constant.ErrorCode;
import org.doubt.dto.GameMessage;
import org.doubt.exception.GameException;
import org.doubt.handler.SessionManager;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class GameController {
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionManager sessionManager;

    @MessageMapping("/game/bet")
    public void processBat(GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionRoomId = (String) Objects.requireNonNull(headerAccessor.getSessionAttributes()).get("roomId");
        if (sessionRoomId == null || !sessionRoomId.equals(message.roomId())) {
            throw new GameException(ErrorCode.NOT_IN_ROOM);
        }
        messagingTemplate.convertAndSend("/topic/room/" + message.roomId(), message);
    }

    @MessageMapping("/game/join")
    public void processJoinRoom(GameMessage message, SimpMessageHeaderAccessor headerAccessor){
        Objects.requireNonNull(headerAccessor.getSessionAttributes()).put("roomId", message.roomId());
        Objects.requireNonNull(headerAccessor.getSessionAttributes()).put("userName", message.sender());

        sessionManager.addUserToRoom(message.roomId(), message.sender());

        messagingTemplate.convertAndSend("/topic/room/" + message.roomId(), message);
    }

}
