package org.doubt.listener;

import org.doubt.auth.NicknameRegistry;
import org.doubt.constant.GameConstants;
import org.doubt.constant.GameStatus;
import org.doubt.constant.PlayerStatus;
import org.doubt.dto.GameMessage;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.PokerRoom;
import org.doubt.dto.RoundState;
import org.doubt.handler.SessionManager;
import org.doubt.repository.PokerRoomRepository;
import org.doubt.service.GameBroadcastService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketEventListenerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private NicknameRegistry nicknameRegistry;

    @Mock
    private GameBroadcastService gameBroadcastService;

    @Mock
    private PokerRoomRepository pokerRoomRepository;

    @Mock
    private ReconnectRegistry reconnectRegistry;

    @Mock
    private org.doubt.service.TournamentService tournamentService;

    @InjectMocks
    private WebSocketEventListener webSocketEventListener;

    // -----------------------------------------------------------------------
    // 헬퍼
    // -----------------------------------------------------------------------

    private SessionDisconnectEvent buildDisconnectEvent(Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("test-session-id");
        if (sessionAttributes != null) {
            accessor.setSessionAttributes(sessionAttributes);
        }
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, "test-session-id", CloseStatus.NORMAL);
    }

    private SessionConnectedEvent buildConnectEvent(Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        accessor.setSessionId("test-session-id");
        if (sessionAttributes != null) {
            accessor.setSessionAttributes(sessionAttributes);
        }
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionConnectedEvent(this, message);
    }

    // -----------------------------------------------------------------------
    // handleWebSocketDisconnectListener
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("handleWebSocketDisconnectListener")
    class HandleWebSocketDisconnectListener {

        @Test
        @DisplayName("getSessionAttributes() 가 null 이면 조기 반환하고 sessionManager 를 호출하지 않는다")
        void nullSessionAttributes_returnsEarly() {
            SessionDisconnectEvent event = buildDisconnectEvent(null);

            assertThatNoException()
                    .isThrownBy(() -> webSocketEventListener.handleWebSocketDisconnectListener(event));

            verify(sessionManager, never()).removeUserFromRoom(any(), any());
            verifyNoInteractions(gameBroadcastService);
        }

        @Test
        @DisplayName("roomId 가 null 이면 조기 반환하고 sessionManager 를 호출하지 않는다")
        void nullRoomId_returnsEarly() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("userName", "alice");

            SessionDisconnectEvent event = buildDisconnectEvent(attrs);

            assertThatNoException()
                    .isThrownBy(() -> webSocketEventListener.handleWebSocketDisconnectListener(event));

            verify(sessionManager, never()).removeUserFromRoom(any(), any());
            verifyNoInteractions(gameBroadcastService);
        }

        @Test
        @DisplayName("userName 이 null 이면 조기 반환하고 sessionManager 를 호출하지 않는다")
        void nullUserName_returnsEarly() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("roomId", "room-1");

            SessionDisconnectEvent event = buildDisconnectEvent(attrs);

            assertThatNoException()
                    .isThrownBy(() -> webSocketEventListener.handleWebSocketDisconnectListener(event));

            verify(sessionManager, never()).removeUserFromRoom(any(), any());
            verifyNoInteractions(gameBroadcastService);
        }

        @Test
        @DisplayName("게임 미진행 중 끊김 — 방에서 제거하고 USER_DISCONNECTED 브로드캐스트")
        void gameNotInProgress_removesUserAndBroadcasts() {
            String roomId = "room-42";
            String userName = "bob";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("roomId", roomId);
            attrs.put("userName", userName);

            // 방이 WAITING 상태 (게임 미진행)
            PokerRoom room = new PokerRoom(roomId, "Test Room");
            room.setStatus(GameStatus.WAITING);
            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

            SessionDisconnectEvent event = buildDisconnectEvent(attrs);
            webSocketEventListener.handleWebSocketDisconnectListener(event);

            verify(sessionManager).removeUserFromRoom(eq(roomId), eq(userName));
            verify(nicknameRegistry).release(eq(userName));

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);
            verify(gameBroadcastService).broadcast(eq(roomId), captor.capture());

            GameMessage sent = captor.getValue();
            assertThat(sent.type()).isEqualTo("USER_DISCONNECTED");
            assertThat(sent.roomId()).isEqualTo(roomId);
            assertThat(sent.sender()).isEqualTo(userName);
        }

        @Test
        @DisplayName("게임 진행 중 첫 끊김 — DISCONNECTED 마킹 후 guestId 포함하여 ReconnectRegistry 에 등록")
        void inGame_firstDisconnect_marksDisconnectedAndTracks() {
            String roomId = "room-1";
            String userName = "charlie";
            String guestId = "guest-uuid-charlie";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("roomId", roomId);
            attrs.put("userName", userName);
            attrs.put("guestId", guestId);

            PokerRoom room = new PokerRoom(roomId, "Test Room");
            room.setStatus(GameStatus.IN_PROGRESS);

            PlayerRoundState prs = new PlayerRoundState();
            prs.setPlayerId(userName);
            prs.setStatus(PlayerStatus.ACTIVE);
            prs.setDisconnectCount(0);

            RoundState state = new RoundState();
            state.setPlayerStates(Map.of(userName, prs));
            room.setRoundState(state);

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

            SessionDisconnectEvent event = buildDisconnectEvent(attrs);
            webSocketEventListener.handleWebSocketDisconnectListener(event);

            assertThat(prs.getStatus()).isEqualTo(PlayerStatus.DISCONNECTED);
            assertThat(prs.getDisconnectCount()).isEqualTo(1);

            verify(reconnectRegistry).track(eq(userName), eq(roomId), eq(guestId));
            verify(sessionManager).removeUserFromRoom(eq(roomId), eq(userName));
            verify(nicknameRegistry).release(eq(userName));

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);
            verify(gameBroadcastService).broadcast(eq(roomId), captor.capture());
            assertThat(captor.getValue().type()).isEqualTo("USER_DISCONNECTED");
        }

        @Test
        @DisplayName("반복 끊김이 MAX_DISCONNECT_COUNT 이상이면 ELIMINATED 자동 패배 처리")
        void inGame_repeatedDisconnect_autoEliminated() {
            String roomId = "room-1";
            String userName = "dave";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("roomId", roomId);
            attrs.put("userName", userName);

            PokerRoom room = new PokerRoom(roomId, "Test Room");
            room.setStatus(GameStatus.IN_PROGRESS);

            PlayerRoundState prs = new PlayerRoundState();
            prs.setPlayerId(userName);
            prs.setStatus(PlayerStatus.DISCONNECTED);
            prs.setDisconnectCount(GameConstants.MAX_DISCONNECT_COUNT - 1); // 한 번 더 끊기면 임계치 도달

            RoundState state = new RoundState();
            state.setPlayerStates(Map.of(userName, prs));
            room.setRoundState(state);

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

            SessionDisconnectEvent event = buildDisconnectEvent(attrs);
            webSocketEventListener.handleWebSocketDisconnectListener(event);

            assertThat(prs.getStatus()).isEqualTo(PlayerStatus.ELIMINATED);
            assertThat(prs.getDisconnectCount()).isEqualTo(GameConstants.MAX_DISCONNECT_COUNT);

            verify(reconnectRegistry, never()).track(any(), any(), any());
            verify(sessionManager).removeUserFromRoom(eq(roomId), eq(userName));

            ArgumentCaptor<GameMessage> captor = ArgumentCaptor.forClass(GameMessage.class);
            verify(gameBroadcastService).broadcast(eq(roomId), captor.capture());
            assertThat(captor.getValue().type()).isEqualTo("AUTO_ELIMINATED");
        }

        @Test
        @DisplayName("roomId 와 userName 이 모두 null 이면 조기 반환하고 sessionManager 를 호출하지 않는다")
        void bothNull_returnsEarly() {
            Map<String, Object> attrs = new HashMap<>();

            SessionDisconnectEvent event = buildDisconnectEvent(attrs);

            assertThatNoException()
                    .isThrownBy(() -> webSocketEventListener.handleWebSocketDisconnectListener(event));

            verify(sessionManager, never()).removeUserFromRoom(any(), any());
            verifyNoInteractions(gameBroadcastService);
        }
    }

    // -----------------------------------------------------------------------
    // handleWebSocketConnectListener
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("handleWebSocketConnectListener")
    class HandleWebSocketConnectListener {

        @Test
        @DisplayName("ReconnectRegistry 에 없으면 신규 연결로 처리하고 상태를 복원하지 않는다")
        void newConnection_noRestore() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", "alice");

            when(reconnectRegistry.findRoom("alice")).thenReturn(Optional.empty());

            SessionConnectedEvent event = buildConnectEvent(attrs);
            webSocketEventListener.handleWebSocketConnectListener(event);

            verify(pokerRoomRepository, never()).findById(any());
            verifyNoInteractions(gameBroadcastService);
        }

        @Test
        @DisplayName("세션 속성에 nickname 이 없으면 조기 반환한다")
        void noNickname_returnsEarly() {
            Map<String, Object> attrs = new HashMap<>();

            SessionConnectedEvent event = buildConnectEvent(attrs);
            webSocketEventListener.handleWebSocketConnectListener(event);

            verify(reconnectRegistry, never()).findRoom(any());
        }

        @Test
        @DisplayName("재접속 — guestId 불일치 시 레지스트리를 클리어하고 상태를 복원하지 않으며 브로드캐스트도 호출하지 않는다")
        void reconnect_guestId_mismatch_clears_registry_and_does_not_restore() {
            String roomId = "room-99";
            String nickname = "bob";
            String correctGuestId = "guest-uuid-correct";
            String wrongGuestId = "guest-uuid-wrong";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", nickname);
            attrs.put("guestId", wrongGuestId);

            when(reconnectRegistry.findRoom(nickname)).thenReturn(Optional.of(roomId));
            when(reconnectRegistry.verifyGuestId(nickname, wrongGuestId)).thenReturn(false);

            SessionConnectedEvent event = buildConnectEvent(attrs);
            webSocketEventListener.handleWebSocketConnectListener(event);

            verify(reconnectRegistry).clear(nickname);
            verify(pokerRoomRepository, never()).findById(any());
            verifyNoInteractions(gameBroadcastService);
            verify(sessionManager, never()).addUserToRoom(any(), any());
        }

        @Test
        @DisplayName("재접속 — guestId 일치 시 DISCONNECTED 플레이어를 ACTIVE 로 복원하고 USER_RECONNECTED 브로드캐스트")
        void reconnect_restoresActiveAndBroadcasts() {
            String roomId = "room-99";
            String nickname = "bob";
            String guestId = "guest-uuid-bob";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", nickname);
            attrs.put("guestId", guestId);

            when(reconnectRegistry.findRoom(nickname)).thenReturn(Optional.of(roomId));
            when(reconnectRegistry.verifyGuestId(nickname, guestId)).thenReturn(true);

            PokerRoom room = new PokerRoom(roomId, "Test Room");
            room.setStatus(GameStatus.IN_PROGRESS);

            PlayerRoundState prs = new PlayerRoundState();
            prs.setPlayerId(nickname);
            prs.setStatus(PlayerStatus.DISCONNECTED);

            RoundState state = new RoundState();
            state.setPlayerStates(new HashMap<>(Map.of(nickname, prs)));
            room.setRoundState(state);

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
            when(nicknameRegistry.register(nickname)).thenReturn(true);

            SessionConnectedEvent event = buildConnectEvent(attrs);
            webSocketEventListener.handleWebSocketConnectListener(event);

            assertThat(prs.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
            assertThat(attrs.get("roomId")).isEqualTo(roomId);
            assertThat(attrs.get("userName")).isEqualTo(nickname);

            verify(reconnectRegistry).clear(nickname);
            verify(sessionManager).addUserToRoom(eq(roomId), eq(nickname));
            verify(gameBroadcastService).broadcastRoundState(eq(roomId), eq("USER_RECONNECTED"), eq(nickname), any(RoundState.class));
        }

        @Test
        @DisplayName("재접속 — broadcastRoundState 는 synchronized 블록 내 캡처된 RoundState 를 사용한다 (레이스 컨디션 방지)")
        void reconnect_broadcast_uses_state_captured_inside_synchronized_block() {
            String roomId = "room-race";
            String nickname = "charlie";
            String guestId = "guest-uuid-charlie";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", nickname);
            attrs.put("guestId", guestId);

            when(reconnectRegistry.findRoom(nickname)).thenReturn(Optional.of(roomId));
            when(reconnectRegistry.verifyGuestId(nickname, guestId)).thenReturn(true);

            PokerRoom room = new PokerRoom(roomId, "Test Room");
            room.setStatus(GameStatus.IN_PROGRESS);

            PlayerRoundState prs = new PlayerRoundState();
            prs.setPlayerId(nickname);
            prs.setStatus(PlayerStatus.DISCONNECTED);

            RoundState stateAtReconnect = new RoundState();
            stateAtReconnect.setPlayerStates(new HashMap<>(Map.of(nickname, prs)));
            room.setRoundState(stateAtReconnect);

            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
            when(nicknameRegistry.register(nickname)).thenReturn(true);

            SessionConnectedEvent event = buildConnectEvent(attrs);
            webSocketEventListener.handleWebSocketConnectListener(event);

            // broadcastRoundState 에 전달된 RoundState 가 synchronized 블록 진입 시점의 인스턴스인지 검증
            verify(gameBroadcastService).broadcastRoundState(
                    eq(roomId), eq("USER_RECONNECTED"), eq(nickname), same(stateAtReconnect));
        }

        @Test
        @DisplayName("재접속 — 방이 존재하지 않으면 상태를 복원하지 않는다")
        void reconnect_roomNotFound_noRestore() {
            String roomId = "ghost-room";
            String nickname = "eve";
            String guestId = "guest-uuid-eve";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", nickname);
            attrs.put("guestId", guestId);

            when(reconnectRegistry.findRoom(nickname)).thenReturn(Optional.of(roomId));
            when(reconnectRegistry.verifyGuestId(nickname, guestId)).thenReturn(true);
            when(pokerRoomRepository.findById(roomId)).thenReturn(Optional.empty());

            SessionConnectedEvent event = buildConnectEvent(attrs);
            webSocketEventListener.handleWebSocketConnectListener(event);

            verify(reconnectRegistry).clear(nickname);
            verify(sessionManager, never()).addUserToRoom(any(), any());
            verifyNoInteractions(gameBroadcastService);
        }
    }
}
