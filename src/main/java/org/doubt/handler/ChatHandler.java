//package org.doubt.handler;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.doubt.dto.ChatMessage;
//import org.springframework.stereotype.Component;
//import org.springframework.web.socket.TextMessage;
//import org.springframework.web.socket.WebSocketSession;
//import org.springframework.web.socket.handler.TextWebSocketHandler;
//import tools.jackson.databind.ObjectMapper;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ChatHandler extends TextWebSocketHandler {
//
//    private final ObjectMapper objectMapper;
//    private final SessionManager sessionManager;
//
//    @Override
//    protected void handleTextMessage(WebSocketSession session, TextMessage message){
//        String payload = message.getPayload();
//        log.info("payload: {}", payload);
//
//        ChatMessage chatMessage = objectMapper.convertValue(payload, ChatMessage.class);
//
//        if(chatMessage.getType().equals(ChatMessage.MessageType.ENTER)){
//            chatMessage.setMessage(chatMessage.getSender() + "is entered the room");
//        }
//
//        broadcastToRoom(chatMessage);
//    }
//
//    private void broadcastToRoom(ChatMessage chatMessage){
//        sessionManager.getSessions(chatMessage.getRoomId()).forEach(session -> {
//            try{
//                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(chatMessage)));
//            } catch(Exception e){
//                log.error("Error sending message: {}", e.getMessage());
//            }
//        });
//    }
//}
