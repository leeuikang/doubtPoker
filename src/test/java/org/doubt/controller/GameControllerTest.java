package org.doubt.controller;

import org.doubt.constant.ErrorCode;
import org.doubt.dto.GameMessage;
import org.doubt.exception.GameException;
import org.doubt.handler.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * GameController 단위 테스트
 * - processBat() 보안 수정(M-4): 세션 roomId 검증 로직
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameController")
class GameControllerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SessionManager sessionManager;

    @InjectMocks
    private GameController gameController;

    private SimpMessageHeaderAccessor headerAccessorWithRoom(String roomId) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        if (roomId != null) {
            attrs.put("roomId", roomId);
        }
        when(accessor.getSessionAttributes()).thenReturn(attrs);
        return accessor;
    }

    private SimpMessageHeaderAccessor headerAccessorEmpty() {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        when(accessor.getSessionAttributes()).thenReturn(new HashMap<>());
        return accessor;
    }

    @Nested
    @DisplayName("processBat")
    class ProcessBat {

        @Test
        @DisplayName("세션 roomId와 메시지 roomId가 일치하면 올바른 토픽으로 메시지를 1회 전송한다")
        void success_when_session_roomId_matches_message_roomId() {
            String roomId = "room-1";
            GameMessage message = new GameMessage("BET", roomId, "player1", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithRoom(roomId);

            gameController.processBat(message, accessor);

            verify(messagingTemplate, times(1))
                    .convertAndSend("/topic/room/" + roomId, message);
        }

        @Test
        @DisplayName("세션에 roomId가 없으면 NOT_IN_ROOM 예외가 발생한다")
        void fail_when_session_roomId_is_null() {
            GameMessage message = new GameMessage("BET", "room-1", "player1", null);
            SimpMessageHeaderAccessor accessor = headerAccessorEmpty();

            assertThatThrownBy(() -> gameController.processBat(message, accessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> {
                        GameException gameException = (GameException) ex;
                        assertThat(gameException.getErrorCode()).isEqualTo(ErrorCode.NOT_IN_ROOM);
                        assertThat(gameException.getMessage())
                                .isEqualTo(ErrorCode.NOT_IN_ROOM.getMessage());
                    });
        }

        @Test
        @DisplayName("세션 roomId와 메시지 roomId가 다르면 NOT_IN_ROOM 예외가 발생한다")
        void fail_when_session_roomId_differs_from_message_roomId() {
            GameMessage message = new GameMessage("BET", "room-2", "player1", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithRoom("room-1");

            assertThatThrownBy(() -> gameController.processBat(message, accessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> {
                        GameException gameException = (GameException) ex;
                        assertThat(gameException.getErrorCode()).isEqualTo(ErrorCode.NOT_IN_ROOM);
                    });
        }

        @Test
        @DisplayName("예외 발생 시 convertAndSend가 호출되지 않는다")
        void does_not_send_message_when_exception_thrown() {
            GameMessage message = new GameMessage("BET", "room-2", "player1", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithRoom("room-1");

            assertThatThrownBy(() -> gameController.processBat(message, accessor))
                    .isInstanceOf(GameException.class);

            verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
        }

        @Test
        @DisplayName("전송 토픽이 /topic/room/{roomId} 형태이다 (이전 버그: message.getClass() 사용)")
        void topic_path_uses_roomId_not_getClass() {
            String roomId = "room-abc";
            GameMessage message = new GameMessage("BET", roomId, "player1", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithRoom(roomId);

            gameController.processBat(message, accessor);

            verify(messagingTemplate).convertAndSend("/topic/room/" + roomId, message);
            verify(messagingTemplate, never())
                    .convertAndSend(contains("GameMessage"), (Object) any());
        }

        @Test
        @DisplayName("NOT_IN_ROOM 예외 메시지가 '방에 참여중이 아닙니다.'이다")
        void not_in_room_exception_message_is_correct() {
            GameMessage message = new GameMessage("BET", "room-1", "player1", null);
            SimpMessageHeaderAccessor accessor = headerAccessorEmpty();

            assertThatThrownBy(() -> gameController.processBat(message, accessor))
                    .isInstanceOf(GameException.class)
                    .hasMessage("방에 참여중이 아닙니다.");
        }
    }
}
