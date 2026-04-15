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
 * SEC-I3 수정 검증 테스트
 *
 * <p>원래 문제(SEC-I3): disconnect 시 {@code nicknameRegistry.release()}를 호출하지만,
 * 재접속 복원 경로({@code restorePlayerInRoom})에서 {@code nicknameRegistry.register()}를
 * 호출하지 않아 레지스트리 일관성이 깨졌다.</p>
 *
 * <p>수정 후: 재접속 플레이어 상태를 ACTIVE로 복원할 때 {@code nicknameRegistry.register(nickname)}
 * 을 호출해 레지스트리를 재등록한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SEC-I3 수정 검증: 재접속 시 NicknameRegistry 재등록")
class SecI3FixTest {

    @Mock private SessionManager sessionManager;
    @Mock private NicknameRegistry nicknameRegistry;
    @Mock private GameBroadcastService gameBroadcastService;
    @Mock private PokerRoomRepository pokerRoomRepository;
    @Mock private ReconnectRegistry reconnectRegistry;
    @Mock private TournamentService tournamentService;

    @InjectMocks
    private WebSocketEventListener listener;

    private PokerRoom room;
    private PlayerRoundState playerState;

    @BeforeEach
    void setUp() {
        room = new PokerRoom("room1", "TestRoom");
        room.setStatus(GameStatus.IN_PROGRESS);

        playerState = new PlayerRoundState();
        playerState.setPlayerId("Alice");
        playerState.setStatus(PlayerStatus.DISCONNECTED);

        RoundState roundState = new RoundState();
        roundState.setPlayerStates(Map.of("Alice", playerState));
        room.setRoundState(roundState);
    }

    // ----------------------------------------------------------------
    // 재접속 시 nicknameRegistry.register 호출 검증
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("DISCONNECTED 플레이어 재접속 — 레지스트리 재등록")
    class RegisterOnReconnect {

        @Test
        @DisplayName("DISCONNECTED 플레이어가 재접속하면 nicknameRegistry.register가 호출된다")
        void disconnected_player_reconnect_registers_nickname() {
            arrangeReconnect("Alice");
            when(nicknameRegistry.register("Alice")).thenReturn(true);

            listener.handleWebSocketConnectListener(buildConnectedEvent("Alice", "g1"));

            verify(nicknameRegistry).register("Alice");
        }

        @Test
        @DisplayName("AI_CONTROLLED 플레이어가 재접속하면 nicknameRegistry.register가 호출된다")
        void ai_controlled_player_reconnect_registers_nickname() {
            playerState.setStatus(PlayerStatus.AI_CONTROLLED);
            arrangeReconnect("Alice");
            when(nicknameRegistry.register("Alice")).thenReturn(true);

            listener.handleWebSocketConnectListener(buildConnectedEvent("Alice", "g1"));

            verify(nicknameRegistry).register("Alice");
        }

        @Test
        @DisplayName("이미 ACTIVE인 플레이어가 재접속하면 register가 호출되지 않는다")
        void already_active_player_does_not_register() {
            playerState.setStatus(PlayerStatus.ACTIVE);
            arrangeReconnect("Alice");

            listener.handleWebSocketConnectListener(buildConnectedEvent("Alice", "g1"));

            verify(nicknameRegistry, never()).register(any());
        }

        @Test
        @DisplayName("register()가 false를 반환하면 플레이어 상태가 복원되지 않는다")
        void register_returns_false_player_not_restored() {
            arrangeReconnect("Alice");
            when(nicknameRegistry.register("Alice")).thenReturn(false);

            listener.handleWebSocketConnectListener(buildConnectedEvent("Alice", "g1"));

            assertThat(playerState.getStatus()).isEqualTo(PlayerStatus.DISCONNECTED);
            verify(gameBroadcastService, never()).broadcastRoundState(any(), any(), any(), any());
        }
    }

    // ----------------------------------------------------------------
    // RoundState가 없을 때 register 미호출 확인
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("복원 실패 경로 — register 호출 안됨")
    class NoRegisterOnFailure {

        @Test
        @DisplayName("RoundState가 null이면 register가 호출되지 않는다")
        void no_round_state_no_register() {
            room.setRoundState(null);
            when(reconnectRegistry.findRoom("Alice")).thenReturn(Optional.of("room1"));
            when(reconnectRegistry.verifyGuestId("Alice", "g1")).thenReturn(true);
            when(pokerRoomRepository.findById("room1")).thenReturn(Optional.of(room));

            listener.handleWebSocketConnectListener(buildConnectedEvent("Alice", "g1"));

            verify(nicknameRegistry, never()).register(any());
        }

        @Test
        @DisplayName("방이 없으면 register가 호출되지 않는다")
        void no_room_no_register() {
            when(reconnectRegistry.findRoom("Alice")).thenReturn(Optional.of("room1"));
            when(reconnectRegistry.verifyGuestId("Alice", "g1")).thenReturn(true);
            when(pokerRoomRepository.findById("room1")).thenReturn(Optional.empty());

            listener.handleWebSocketConnectListener(buildConnectedEvent("Alice", "g1"));

            verify(nicknameRegistry, never()).register(any());
        }
    }

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private void arrangeReconnect(String nickname) {
        when(reconnectRegistry.findRoom(nickname)).thenReturn(Optional.of("room1"));
        when(reconnectRegistry.verifyGuestId(eq(nickname), any())).thenReturn(true);
        when(pokerRoomRepository.findById("room1")).thenReturn(Optional.of(room));
    }

    private SessionConnectedEvent buildConnectedEvent(String nickname, String guestId) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("nickname", nickname);
        attrs.put("guestId", guestId);
        Map<String, Object> headers = new HashMap<>();
        headers.put("simpSessionAttributes", attrs);
        Message<byte[]> message = new GenericMessage<>(new byte[0], new MessageHeaders(headers));
        return new SessionConnectedEvent(this, message);
    }
}
