package org.doubt.controller;

import org.doubt.dto.ChatMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    @MessageMapping("/chat/message")
    @SendTo("/topic/chat")
    public ChatMessage sendMessage(ChatMessage message) {
        return message;
    }
}
