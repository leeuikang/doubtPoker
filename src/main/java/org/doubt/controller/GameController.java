package org.doubt.controller;

import lombok.RequiredArgsConstructor;
import org.doubt.dto.GameMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class GameController {
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/game/bet")
    public void processBat(GameMessage message){
        messagingTemplate.convertAndSend("/topic/room" + message.getClass(), message);
    }

}
