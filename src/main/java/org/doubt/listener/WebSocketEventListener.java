package org.doubt.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 연결/해제 이벤트 핸들러
 *
 * <h3>연결 끊김 처리</h3>
 * <ul>
 *   <li>게임 진행 중: {@link PlayerStatus#DISCONNECTED} 마킹 → AI 인계 대기,
 *       {@link ReconnectRegistry}에 닉네임→방 ID 등록</li>
 *   <li>반복 끊김({@link GameConstants#MAX_DISCONNECT_COUNT} 이상):
 *       {@link PlayerStatus#ELIMINATED} 자동 패배 처리 (재접속 추적 안 함)</li>
 *   <li>게임 미진행 중: 방에서 즉시 제거, 닉네임 해제</li>
 * </ul>
 *
 * <h3>재접속 복원</h3>
 * <ul>
 *   <li>{@link SessionConnectedEvent} 시점에 {@link ReconnectRegistry}를 조회해
 *       복귀 플레이어를 감지한다.</li>
 *   <li>감지되면: 플레이어 상태를 {@link PlayerStatus#ACTIVE}로 복원하고,
 *       세션 속성에 방 ID를 재설정한 뒤 방 전체에 현재 상태를 브로드캐스트한다.</li>
 * </ul>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SessionManager sessionManager;
    private final NicknameRegistry nicknameRegistry;
    private final SimpMessageSendingOperations messagingTemplate;
    private final PokerRoomRepository pokerRoomRepository;
    private final ReconnectRegistry reconnectRegistry;

    // ─────────────────────────────────────────────────────────
    // 재접속 복원
    // ─────────────────────────────────────────────────────────

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> attrs = sha.getSessionAttributes();
        if (attrs == null) return;

        String nickname = (String) attrs.get("nickname");
        if (nickname == null) return;

        Optional<String> roomIdOpt = reconnectRegistry.findRoom(nickname);
        if (roomIdOpt.isEmpty()) {
            log.info("[WS] New connection: nickname={}", nickname);
            return;
        }

        String roomId = roomIdOpt.get();
        reconnectRegistry.clear(nickname);

        PokerRoom room = pokerRoomRepository.findById(roomId).orElse(null);
        if (room == null) {
            log.warn("[WS] Reconnect attempted but room not found: nickname={}, roomId={}", nickname, roomId);
            return;
        }

        synchronized (room) {
            RoundState state = room.getRoundState();
            if (state == null) return;

            PlayerRoundState prs = state.getPlayerStates().get(nickname);
            if (prs == null) return;

            if (prs.getStatus() == PlayerStatus.DISCONNECTED || prs.getStatus() == PlayerStatus.AI_CONTROLLED) {
                prs.setStatus(PlayerStatus.ACTIVE);
                log.info("[WS] Player reconnected and restored: nickname={}, roomId={}", nickname, roomId);
            }

            sessionManager.addUserToRoom(roomId, nickname);
            attrs.put("roomId", roomId);
            attrs.put("userName", nickname);
        }

        broadcast(roomId, new GameMessage("USER_RECONNECTED", roomId, nickname, room.getRoundState()));
    }

    // ─────────────────────────────────────────────────────────
    // 연결 끊김 처리
    // ─────────────────────────────────────────────────────────

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        Map<String, Object> attributes = headerAccessor.getSessionAttributes();
        if (attributes == null) return;

        Object roomIdObj = attributes.get("roomId");
        Object userNameObj = attributes.get("userName");
        if (roomIdObj == null || userNameObj == null) return;

        String roomId = roomIdObj.toString();
        String userName = userNameObj.toString();

        log.info("[WS] User disconnected: nickname={}, roomId={}", userName, roomId);

        PokerRoom room = pokerRoomRepository.findById(roomId).orElse(null);
        if (room != null && room.getStatus() == GameStatus.IN_PROGRESS) {
            handleInGameDisconnect(room, roomId, userName);
        } else {
            // 게임 미진행 중: 즉시 제거
            sessionManager.removeUserFromRoom(roomId, userName);
            nicknameRegistry.release(userName);
            broadcast(roomId, new GameMessage("USER_DISCONNECTED", roomId, userName, null));
        }
    }

    // ─────────────────────────────────────────────────────────
    // private helpers
    // ─────────────────────────────────────────────────────────

    /**
     * 게임 진행 중 연결 끊김을 처리한다.
     *
     * <ul>
     *   <li>disconnectCount를 증가시키고 임계치 이상이면 자동 패배(ELIMINATED) 처리한다.</li>
     *   <li>임계치 미만이면 DISCONNECTED 마킹 후 ReconnectRegistry에 등록한다.</li>
     * </ul>
     */
    private void handleInGameDisconnect(PokerRoom room, String roomId, String userName) {
        synchronized (room) {
            RoundState state = room.getRoundState();
            if (state == null) {
                fallbackDisconnect(roomId, userName);
                return;
            }

            PlayerRoundState prs = state.getPlayerStates().get(userName);
            if (prs == null) {
                fallbackDisconnect(roomId, userName);
                return;
            }

            prs.setDisconnectCount(prs.getDisconnectCount() + 1);

            if (prs.getDisconnectCount() >= GameConstants.MAX_DISCONNECT_COUNT) {
                // 자동 패배 처리
                prs.setStatus(PlayerStatus.ELIMINATED);
                sessionManager.removeUserFromRoom(roomId, userName);
                nicknameRegistry.release(userName);
                log.warn("[WS] Auto-eliminated due to repeated disconnects: nickname={}, count={}", userName, prs.getDisconnectCount());
                broadcast(roomId, new GameMessage("AUTO_ELIMINATED", roomId, userName, null));
            } else {
                // 재접속 대기 (AI 인계는 TimerService/GameController에서 수행)
                prs.setStatus(PlayerStatus.DISCONNECTED);
                sessionManager.removeUserFromRoom(roomId, userName);
                nicknameRegistry.release(userName);
                reconnectRegistry.track(userName, roomId);
                log.info("[WS] Player marked DISCONNECTED, awaiting reconnect: nickname={}, disconnectCount={}", userName, prs.getDisconnectCount());
                broadcast(roomId, new GameMessage("USER_DISCONNECTED", roomId, userName, null));
            }
        }
    }

    /** RoundState가 없거나 PlayerRoundState를 찾을 수 없는 경우의 fallback. */
    private void fallbackDisconnect(String roomId, String userName) {
        sessionManager.removeUserFromRoom(roomId, userName);
        nicknameRegistry.release(userName);
        broadcast(roomId, new GameMessage("USER_DISCONNECTED", roomId, userName, null));
    }

    private void broadcast(String roomId, GameMessage message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }
}
