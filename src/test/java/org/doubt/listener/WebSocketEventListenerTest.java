package org.doubt.listener;

import org.doubt.dto.GameMessage;
import org.doubt.handler.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private WebSocketEventListener webSocketEventListener;

    // -----------------------------------------------------------------------
    // 헬퍼: DISCONNECT STOMP 메시지 빌드
    // -----------------------------------------------------------------------

    /**
     * StompHeaderAccessor.wrap() 이 읽어들이는 MESSAGE 헤더에
     * SESSION_ATTRIBUTES 를 심어 실제 getSessionAttributes() 가
     * 원하는 Map 을 반환하도록 메시지를 조립한다.
     */
    private SessionDisconnectEvent buildEvent(Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("test-session-id");
        if (sessionAttributes != null) {
            accessor.setSessionAttributes(sessionAttributes);
        }
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, "test-session-id", CloseStatus.NORMAL);
    }

    // -----------------------------------------------------------------------
    // 테스트
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("handleWebSocketDisconnectListener")
    class HandleWebSocketDisconnectListener {

        @Test
        @DisplayName("getSessionAttributes() 가 null 을 반환하면 조기 반환하고 sessionManager 를 호출하지 않는다")
        void nullSessionAttributes_returnsEarly_noSessionManagerCall() {
            // sessionAttributes 를 설정하지 않으면 getSessionAttributes() 는 null 반환
            SessionDisconnectEvent event = buildEvent(null);

            assertThatNoException()
                    .isThrownBy(() -> webSocketEventListener.handleWebSocketDisconnectListener(event));

            verify(sessionManager, never()).removeUserFromRoom(any(), any());
            verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        }

        @Test
        @DisplayName("sessionAttributes 는 존재하지만 roomId 가 null 이면 조기 반환하고 sessionManager 를 호출하지 않는다")
        void nullRoomId_returnsEarly_noSessionManagerCall() {
            Map<String, Object> attributes = new HashMap<>();
            // roomId 는 없고 userName 만 있는 상황
            attributes.put("userName", "alice");

            SessionDisconnectEvent event = buildEvent(attributes);

            assertThatNoException()
                    .isThrownBy(() -> webSocketEventListener.handleWebSocketDisconnectListener(event));

            verify(sessionManager, never()).removeUserFromRoom(any(), any());
            verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        }

        @Test
        @DisplayName("sessionAttributes 는 존재하지만 userName 이 null 이면 조기 반환하고 sessionManager 를 호출하지 않는다")
        void nullUserName_returnsEarly_noSessionManagerCall() {
            Map<String, Object> attributes = new HashMap<>();
            // userName 은 없고 roomId 만 있는 상황
            attributes.put("roomId", "room-1");

            SessionDisconnectEvent event = buildEvent(attributes);

            assertThatNoException()
                    .isThrownBy(() -> webSocketEventListener.handleWebSocketDisconnectListener(event));

            verify(sessionManager, never()).removeUserFromRoom(any(), any());
            verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        }

        @Test
        @DisplayName("roomId 와 userName 이 모두 존재하면 sessionManager.removeUserFromRoom() 을 호출하고 /topic/room/{roomId} 로 메시지를 전송한다")
        void validAttributes_callsRemoveAndSendsMessage() {
            String roomId = "room-42";
            String userName = "bob";

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("roomId", roomId);
            attributes.put("userName", userName);

            SessionDisconnectEvent event = buildEvent(attributes);

            webSocketEventListener.handleWebSocketDisconnectListener(event);

            // sessionManager 호출 검증
            verify(sessionManager).removeUserFromRoom(eq(roomId), eq(userName));

            // 메시지 전송 검증 — 목적지와 GameMessage 내용 확인
            ArgumentCaptor<GameMessage> messageCaptor = ArgumentCaptor.forClass(GameMessage.class);
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/room/" + roomId),
                    messageCaptor.capture()
            );

            GameMessage sent = messageCaptor.getValue();
            assertThat(sent.type()).isEqualTo(userName + " user-disconnected");
            assertThat(sent.roomId()).isEqualTo(roomId);
            assertThat(sent.sender()).isEqualTo(userName);
            assertThat(sent.payload()).isNull();
        }

        @Test
        @DisplayName("roomId 와 userName 이 모두 null 이면 조기 반환하고 sessionManager 를 호출하지 않는다")
        void bothRoomIdAndUserNameNull_returnsEarly_noSessionManagerCall() {
            Map<String, Object> attributes = new HashMap<>();
            // 빈 맵 — roomId, userName 모두 없음

            SessionDisconnectEvent event = buildEvent(attributes);

            assertThatNoException()
                    .isThrownBy(() -> webSocketEventListener.handleWebSocketDisconnectListener(event));

            verify(sessionManager, never()).removeUserFromRoom(any(), any());
            verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        }
    }
}
