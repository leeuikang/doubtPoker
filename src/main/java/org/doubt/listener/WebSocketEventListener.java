package org.doubt.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.dto.GameMessage;
import org.doubt.handler.SessionManager;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SessionManager sessionManager;
    private final SimpMessageSendingOperations messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event){
        log.info("Received a new web socket connection");
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event){
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String roomId = headerAccessor.getSessionAttributes().get("roomId").toString();
        String userName = headerAccessor.getSessionAttributes().get("userName").toString();

        if(roomId != null && userName != null) {
            log.info("User Disconnected: " + userName);

            sessionManager.removeUserFromRoom(roomId, userName);

            GameMessage message = new GameMessage(userName+" user-disconnected", roomId,
                    userName, null);

            messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
        }
    }
}
