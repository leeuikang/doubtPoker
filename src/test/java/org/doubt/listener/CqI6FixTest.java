package org.doubt.listener;

import org.doubt.auth.NicknameRegistry;
import org.doubt.constant.GameStatus;
import org.doubt.constant.PlayerStatus;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.PokerRoom;
import org.doubt.dto.RoundState;
import org.doubt.handler.SessionManager;
import org.doubt.repository.PokerRoomRepository;
import org.doubt.service.GameBroadcastService;
import org.doubt.service.TournamentService;
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
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CQ-I6 수정 검증 테스트
 *
 * <p>원래 문제(CQ-I6): {@code handleWebSocketConnectListener}에서
 * {@code capturedState = null}로 초기화 후 synchronized 블록 안에서 할당하는 패턴.
 * Optional을 반환하는 {@code restorePlayerInRoom()} 헬퍼로 추출.</p>
 *
 * <p>수정 후: synchronized 블록이 Optional을 반환하며, null 초기화 패턴이 제거됨.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CQ-I6 수정 검증: WebSocketEventListener restorePlayerInRoom Optional 헬퍼")
class CqI6FixTest {

    @Mock private SessionManager sessionManager;
    @Mock private NicknameRegistry nicknameRegistry;
    @Mock private GameBroadcastService gameBroadcastService;
    @Mock private PokerRoomRepository pokerRoomRepository;
    @Mock private ReconnectRegistry reconnectRegistry;
    @Mock private TournamentService tournamentService;

    @InjectMocks
    private WebSocketEventListener listener;

    private PokerRoom room;
    private RoundState roundState;
    private PlayerRoundState playerState;

    @BeforeEach
    void setUp() {
        room = new PokerRoom("room1", "TestRoom");
        room.setStatus(GameStatus.IN_PROGRESS);

        playerState = new PlayerRoundState();
        playerState.setPlayerId("Alice");
        playerState.setStatus(PlayerStatus.DISCONNECTED);

        roundState = new RoundState();
        roundState.setPlayerStates(Map.of("Alice", playerState));

        room.setRoundState(roundState);
    }

    @Nested
    @DisplayName("재접속 성공 — broadcastRoundState 호출됨")
    class ReconnectSuccess {

        @Test
        @DisplayName("DISCONNECTED 플레이어가 재접속하면 ACTIVE로 복원되고 브로드캐스트된다")
        void disconnected_player_reconnects_and_state_is_broadcast() {
            Map<String, Object> attrs = buildAttrs("Alice", "guest-1", "room1");
            when(reconnectRegistry.findRoom("Alice")).thenReturn(Optional.of("room1"));
            when(reconnectRegistry.verifyGuestId("Alice", "guest-1")).thenReturn(true);
            when(pokerRoomRepository.findById("room1")).thenReturn(Optional.of(room));

            listener.handleWebSocketConnectListener(buildConnectedEvent(attrs));

            assertThat(playerState.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
            verify(gameBroadcastService).broadcastRoundState(
                    eq("room1"), eq("USER_RECONNECTED"), eq("Alice"), any(RoundState.class));
        }

        @Test
        @DisplayName("AI_CONTROLLED 플레이어도 재접속 시 ACTIVE로 복원된다")
        void ai_controlled_player_reconnects_and_becomes_active() {
            playerState.setStatus(PlayerStatus.AI_CONTROLLED);
            Map<String, Object> attrs = buildAttrs("Alice", "guest-1", "room1");
            when(reconnectRegistry.findRoom("Alice")).thenReturn(Optional.of("room1"));
            when(reconnectRegistry.verifyGuestId("Alice", "guest-1")).thenReturn(true);
            when(pokerRoomRepository.findById("room1")).thenReturn(Optional.of(room));

            listener.handleWebSocketConnectListener(buildConnectedEvent(attrs));

            assertThat(playerState.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("재접속 실패 — broadcastRoundState 호출 안됨")
    class ReconnectFailure {

        @Test
        @DisplayName("RoundState가 null이면 브로드캐스트되지 않는다")
        void no_round_state_means_no_broadcast() {
            room.setRoundState(null);
            Map<String, Object> attrs = buildAttrs("Alice", "guest-1", "room1");
            when(reconnectRegistry.findRoom("Alice")).thenReturn(Optional.of("room1"));
            when(reconnectRegistry.verifyGuestId("Alice", "guest-1")).thenReturn(true);
            when(pokerRoomRepository.findById("room1")).thenReturn(Optional.of(room));

            listener.handleWebSocketConnectListener(buildConnectedEvent(attrs));

            verify(gameBroadcastService, never()).broadcastRoundState(any(), any(), any(), any());
        }

        @Test
        @DisplayName("방이 없으면 브로드캐스트되지 않는다")
        void no_room_means_no_broadcast() {
            Map<String, Object> attrs = buildAttrs("Alice", "guest-1", "room1");
            when(reconnectRegistry.findRoom("Alice")).thenReturn(Optional.of("room1"));
            when(reconnectRegistry.verifyGuestId("Alice", "guest-1")).thenReturn(true);
            when(pokerRoomRepository.findById("room1")).thenReturn(Optional.empty());

            listener.handleWebSocketConnectListener(buildConnectedEvent(attrs));

            verify(gameBroadcastService, never()).broadcastRoundState(any(), any(), any(), any());
        }

        @Test
        @DisplayName("닉네임이 null이면 재접속 흐름 자체가 실행되지 않는다")
        void null_nickname_skips_reconnect() {
            Map<String, Object> attrs = new HashMap<>();
            // nickname 없음

            listener.handleWebSocketConnectListener(buildConnectedEvent(attrs));

            verify(reconnectRegistry, never()).findRoom(any());
        }
    }

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private Map<String, Object> buildAttrs(String nickname, String guestId, String roomId) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("nickname", nickname);
        attrs.put("guestId", guestId);
        attrs.put("roomId", roomId);
        return attrs;
    }

    private SessionConnectedEvent buildConnectedEvent(Map<String, Object> sessionAttributes) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("simpSessionAttributes", sessionAttributes);
        Message<byte[]> message = new GenericMessage<>(new byte[0], new MessageHeaders(headers));
        return new SessionConnectedEvent(this, message);
    }
}
