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

import java.util.Map;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class GameController {
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionManager sessionManager;

    @MessageMapping("/game/bet")
    public void processBat(GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = Objects.requireNonNull(headerAccessor.getSessionAttributes());

        String sessionRoomId = (String) attrs.get("roomId");
        if (sessionRoomId == null || !sessionRoomId.equals(message.roomId())) {
            throw new GameException(ErrorCode.NOT_IN_ROOM);
        }

        // 브로드캐스트 sender를 세션 검증 nickname으로 교체 (사칭 차단)
        String nickname = (String) attrs.get("nickname");
        if (nickname == null) throw new GameException(ErrorCode.UNAUTHORIZED);

        GameMessage verified = new GameMessage(message.type(), message.roomId(), nickname, message.payload());
        messagingTemplate.convertAndSend("/topic/room/" + message.roomId(), verified);
    }

    @MessageMapping("/game/join")
    public void processJoinRoom(GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = Objects.requireNonNull(headerAccessor.getSessionAttributes());

        // CONNECT 시 StompAuthInterceptor가 저장한 검증된 identity 사용 (사칭 차단)
        String nickname = (String) attrs.get("nickname");
        if (nickname == null) throw new GameException(ErrorCode.UNAUTHORIZED);

        attrs.put("roomId", message.roomId());
        attrs.put("userName", nickname);

        sessionManager.addUserToRoom(message.roomId(), nickname);

        // 브로드캐스트 메시지의 sender도 검증된 nickname으로 교체
        GameMessage verified = new GameMessage(message.type(), message.roomId(), nickname, message.payload());
        messagingTemplate.convertAndSend("/topic/room/" + message.roomId(), verified);
    }

}
