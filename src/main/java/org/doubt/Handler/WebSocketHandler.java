package org.doubt.Handler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler{
    private final List<WebSocketSession> sessionList = new CopyOnWriteArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionList.add(session);
    }

    @Override
    protected  void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        for(WebSocketSession webSocketSession : sessionList) {
            if(webSocketSession.isOpen()) {
                try{
                    webSocketSession.sendMessage(new TextMessage("응답: " +payload));
                } catch(IOException e){
                    e.printStackTrace();
                }
            }
        }
    }
}