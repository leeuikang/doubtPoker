package org.doubt.controller;

import org.doubt.constant.ErrorCode;
import org.doubt.dto.ChatMessage;
import org.doubt.exception.GameException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Objects;

@Controller
public class ChatController {

    /**
     * 채팅 메시지 전송
     *
     * <p>GL-W2: 클라이언트가 전달한 {@code sender} 필드를 그대로 사용하면 닉네임 사칭이 가능하다.
     * 세션 속성의 {@code nickname}으로 {@code sender}를 강제 설정해 사칭을 방지한다.</p>
     */
    @MessageMapping("/chat/message")
    @SendTo("/topic/chat")
    public ChatMessage sendMessage(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = Objects.requireNonNull(headerAccessor.getSessionAttributes());
        String nickname = (String) attrs.get("nickname");
        if (nickname == null) throw new GameException(ErrorCode.UNAUTHORIZED);
        message.setSender(nickname); // GL-W2: 클라이언트 제공 sender 무시, 세션 인증값 강제 적용
        return message;
    }
}
