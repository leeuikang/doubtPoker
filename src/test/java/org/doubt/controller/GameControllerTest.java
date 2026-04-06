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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * GameController 단위 테스트
 * - processBat() 보안 수정(M-4): 세션 roomId 검증 로직
 * - processJoinRoom() 인증(H-2): 세션 nickname 사용 및 UNAUTHORIZED 처리
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
        return headerAccessorWithRoomAndNickname(roomId, "player1");
    }

    private SimpMessageHeaderAccessor headerAccessorWithRoomAndNickname(String roomId, String nickname) {
        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        if (roomId != null) attrs.put("roomId", roomId);
        if (nickname != null) attrs.put("nickname", nickname);
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
        @DisplayName("세션 roomId와 메시지 roomId가 일치하면 올바른 토픽으로 메시지를 1회 전송한다 (sender는 세션 nickname)")
        void success_when_session_roomId_matches_message_roomId() {
            String roomId = "room-1";
            String nickname = "player1";
            GameMessage message = new GameMessage("BET", roomId, "imposter", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithRoomAndNickname(roomId, nickname);

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);
            gameController.processBat(message, accessor);

            verify(messagingTemplate, times(1))
                    .convertAndSend(eq("/topic/room/" + roomId), captor.capture());
            assertThat(captor.getValue().sender()).isEqualTo(nickname);
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

            verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId), any(GameMessage.class));
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

        @Test
        @DisplayName("roomId는 일치하지만 세션에 nickname이 없으면 UNAUTHORIZED 예외가 발생한다")
        void fail_when_nickname_missing_from_session() {
            String roomId = "room-1";
            GameMessage message = new GameMessage("BET", roomId, "player1", null);
            // roomId는 있지만 nickname은 없는 세션
            SimpMessageHeaderAccessor accessor = headerAccessorWithRoomAndNickname(roomId, null);

            assertThatThrownBy(() -> gameController.processBat(message, accessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));
        }
    }

    @Nested
    @DisplayName("processJoinRoom")
    class ProcessJoinRoom {

        private SimpMessageHeaderAccessor headerAccessorWithNickname(String nickname) {
            SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
            Map<String, Object> attrs = new HashMap<>();
            if (nickname != null) {
                attrs.put("nickname", nickname);
            }
            when(accessor.getSessionAttributes()).thenReturn(attrs);
            return accessor;
        }

        @Test
        @DisplayName("세션에 nickname이 있으면 sessionManager.addUserToRoom이 세션 닉네임으로 호출된다")
        void session_nickname_is_used_for_addUserToRoom_not_message_sender() {
            String sessionNickname = "검증된닉네임";
            String messageSender = "사칭자";
            String roomId = "room-1";
            GameMessage message = new GameMessage("JOIN", roomId, messageSender, null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithNickname(sessionNickname);

            gameController.processJoinRoom(message, accessor);

            verify(sessionManager, times(1)).addUserToRoom(roomId, sessionNickname);
            verify(sessionManager, never()).addUserToRoom(roomId, messageSender);
        }

        @Test
        @DisplayName("브로드캐스트 GameMessage의 sender는 세션 nickname이고 message.sender()가 아니다")
        void broadcast_sender_is_replaced_with_session_nickname() {
            String sessionNickname = "검증된닉네임";
            String messageSender = "사칭자";
            String roomId = "room-1";
            GameMessage message = new GameMessage("JOIN", roomId, messageSender, null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithNickname(sessionNickname);

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);

            gameController.processJoinRoom(message, accessor);

            verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId), captor.capture());
            GameMessage broadcast = captor.getValue();
            assertThat(broadcast.sender()).isEqualTo(sessionNickname);
            assertThat(broadcast.sender()).isNotEqualTo(messageSender);
        }

        @Test
        @DisplayName("세션에 nickname이 없으면 UNAUTHORIZED 예외가 발생한다")
        void null_nickname_in_session_throws_unauthorized() {
            GameMessage message = new GameMessage("JOIN", "room-1", "anyone", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithNickname(null);

            assertThatThrownBy(() -> gameController.processJoinRoom(message, accessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(sessionManager, never()).addUserToRoom(any(), any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), (Object) any());
        }

        @Test
        @DisplayName("정상 처리 후 세션 attrs에 roomId와 userName이 올바르게 저장된다")
        void session_attrs_are_updated_with_roomId_and_userName() {
            String sessionNickname = "홍길동";
            String roomId = "room-99";
            GameMessage message = new GameMessage("JOIN", roomId, "imposter", null);

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", sessionNickname);
            SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
            when(accessor.getSessionAttributes()).thenReturn(attrs);

            gameController.processJoinRoom(message, accessor);

            assertThat(attrs.get("roomId")).isEqualTo(roomId);
            assertThat(attrs.get("userName")).isEqualTo(sessionNickname);
        }

        @Test
        @DisplayName("UNAUTHORIZED 예외 메시지가 '인증이 필요합니다.'이다")
        void unauthorized_exception_message_is_correct() {
            GameMessage message = new GameMessage("JOIN", "room-1", "anyone", null);
            SimpMessageHeaderAccessor accessor = headerAccessorWithNickname(null);

            assertThatThrownBy(() -> gameController.processJoinRoom(message, accessor))
                    .isInstanceOf(GameException.class)
                    .hasMessage("인증이 필요합니다.");
        }
    }
}
