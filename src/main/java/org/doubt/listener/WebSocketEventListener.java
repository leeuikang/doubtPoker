package org.doubt.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.auth.NicknameRegistry;
import org.doubt.dto.GameMessage;
import org.doubt.handler.SessionManager;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SessionManager sessionManager;
    private final NicknameRegistry nicknameRegistry;
    private final SimpMessageSendingOperations messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event){
        log.info("Received a new web socket connection");
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event){
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        Map<String, Object> attributes = headerAccessor.getSessionAttributes();
        if (attributes == null) {
            return;
        }

        Object roomIdObj = attributes.get("roomId");
        Object userNameObj = attributes.get("userName");
        if (roomIdObj == null || userNameObj == null) {
            return;
        }

        String roomId = roomIdObj.toString();
        String userName = userNameObj.toString();

        log.info("User Disconnected: {}", userName);

        sessionManager.removeUserFromRoom(roomId, userName);
        nicknameRegistry.release(userName);

        GameMessage message = new GameMessage(userName + " user-disconnected", roomId, userName, null);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }
}
