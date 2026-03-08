package org.doubt.controller;

import lombok.RequiredArgsConstructor;
import org.doubt.dto.GameMessage;
import org.doubt.handler.SessionManager;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GameController {
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionManager sessionManager;

    @MessageMapping("/game/bet")
    public void processBat(GameMessage message){
        messagingTemplate.convertAndSend("/topic/room" + message.getClass(), message);
    }

    @MessageMapping("/game/join")
    public void processJoinRoom(GameMessage message, SimpMessageHeaderAccessor headerAccessor){
        headerAccessor.getSessionAttributes().put("roomId", message.roomId());
        headerAccessor.getSessionAttributes().put("userName", message.sender());

        sessionManager.addUserToRoom(message.roomId(), message.sender());

        messagingTemplate.convertAndSend("/topic/room/" + message.roomId(), message);
    }

}
