package org.doubt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.ErrorCode;
import org.doubt.constant.GameAction;
import org.doubt.constant.GameConstants;
import org.doubt.constant.GameStatus;
import org.doubt.constant.PlayerStatus;
import org.doubt.dto.GameMessage;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.PokerPlayer;
import org.doubt.dto.PokerRoom;
import org.doubt.dto.RoundState;
import org.doubt.dto.TournamentState;
import org.doubt.dto.request.DiscardRequest;
import org.doubt.dto.request.DoubtRequest;
import org.doubt.dto.request.DrawRequest;
import org.doubt.dto.request.ExtendRequest;
import org.doubt.dto.request.MeldRequest;
import org.doubt.dto.request.RevealBluffRequest;
import org.doubt.dto.request.StopRequest;
import org.doubt.exception.GameException;
import org.doubt.handler.SessionManager;
import org.doubt.repository.PokerRoomRepository;
import org.doubt.service.GameBroadcastService;
import org.doubt.service.RoundService;
import org.doubt.service.TournamentService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * WebSocket 게임 액션 컨트롤러
 *
 * <p>클라이언트 → 서버 경로:
 * <ul>
 *   <li>{@code /app/game/join}        — 방 입장</li>
 *   <li>{@code /app/game/action}      — 턴 액션 라우팅 (GameAction 기반)</li>
 *   <li>{@code /app/game/round/start} — 새 라운드 시작</li>
 * </ul>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class GameController {

    private final GameBroadcastService gameBroadcastService;
    private final SessionManager sessionManager;
    private final RoundService roundService;
    private final TournamentService tournamentService;
    private final PokerRoomRepository pokerRoomRepository;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────
    // 방 입장
    // ─────────────────────────────────────────────────────────

    @MessageMapping("/game/join")
    public void processJoinRoom(GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = Objects.requireNonNull(headerAccessor.getSessionAttributes());

        String nickname = (String) attrs.get("nickname");
        if (nickname == null) throw new GameException(ErrorCode.UNAUTHORIZED);

        // 방 존재 여부 먼저 검증 — 없으면 SessionManager 상태 오염 방지 (R2-W5)
        findRoom(message.roomId());

        attrs.put("roomId", message.roomId());
        attrs.put("userName", nickname);

        sessionManager.addUserToRoom(message.roomId(), nickname);

        // SEC-M4: 첫 입장 플레이어를 방장으로 지정
        PokerRoom joinedRoom = findRoom(message.roomId());
        synchronized (joinedRoom) {
            if (joinedRoom.getHostId() == null) {
                joinedRoom.setHostId(nickname);
            }
        }

        GameMessage verified = new GameMessage(message.type(), message.roomId(), nickname, message.payload());
        gameBroadcastService.broadcast(message.roomId(), verified);
    }

    // ─────────────────────────────────────────────────────────
    // 새 라운드 시작
    // ─────────────────────────────────────────────────────────

    /**
     * 새 라운드를 시작한다.
     *
     * <p>payload: {@code { "firstPlayerId": "<nickname>" }} (선 플레이어 지정).
     * 생략 시 방의 첫 번째 플레이어가 선이 된다.</p>
     */
    @MessageMapping("/game/round/start")
    public void processStartRound(GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = Objects.requireNonNull(headerAccessor.getSessionAttributes());
        String nickname = (String) attrs.get("nickname");
        if (nickname == null) throw new GameException(ErrorCode.UNAUTHORIZED);

        String sessionRoomId = (String) attrs.get("roomId");
        if (sessionRoomId == null || !sessionRoomId.equals(message.roomId())) {
            throw new GameException(ErrorCode.NOT_IN_ROOM);
        }

        PokerRoom room = findRoom(message.roomId());

        RoundState startedState;
        int currentRound;

        synchronized (room) {
            // SEC-M4: 방장만 라운드를 시작할 수 있다
            if (!nickname.equals(room.getHostId())) {
                throw new GameException(ErrorCode.NOT_HOST);
            }

            if (room.getStatus() == GameStatus.TOURNAMENT_END) {
                throw new GameException(ErrorCode.INVALID_TURN_PHASE);
            }

            List<String> allPlayerIds = room.getPlayerList().stream()
                    .map(PokerPlayer::getName)
                    .toList();

            TournamentState tournament = room.getTournamentState();
            List<String> playerIds;
            String firstPlayerId;

            if (tournament == null) {
                // 첫 라운드: 토너먼트 초기화
                tournament = tournamentService.initTournament(message.roomId(), allPlayerIds);
                room.setTournamentState(tournament);
                playerIds = allPlayerIds;
                firstPlayerId = extractFirstPlayerId(message.payload(), playerIds);
            } else {
                // 이후 라운드: 탈락자 제외, 직전 승자가 선
                playerIds = tournamentService.getActivePlayers(tournament).stream()
                        .filter(allPlayerIds::contains)
                        .toList();
                if (playerIds.size() < GameConstants.MIN_PLAYERS) {
                    throw new GameException(ErrorCode.NOT_ENOUGH_PLAYERS);
                }
                String lastWinner = tournament.getLastRoundWinnerId();
                firstPlayerId = (lastWinner != null && playerIds.contains(lastWinner))
                        ? lastWinner
                        : playerIds.get(0);
            }

            startedState = roundService.startRound(message.roomId(), playerIds, firstPlayerId);
            room.setRoundState(startedState);
            room.setStatus(GameStatus.IN_PROGRESS);
            room.updateActivityTime();
            currentRound = room.getTournamentState().getCurrentRound();
        }

        gameBroadcastService.broadcastRoundState(message.roomId(), "ROUND_STARTED", "SYSTEM", startedState);
        log.info("[GameController] round started roomId={} round={}", message.roomId(), currentRound);
    }

    // ─────────────────────────────────────────────────────────
    // 턴 액션 라우팅
    // ─────────────────────────────────────────────────────────

    /**
     * GameAction 기반 턴 액션 라우팅.
     *
     * <p>메시지 {@code type} 필드에 {@link GameAction} 이름을 담아 전송한다.
     * 처리 후 갱신된 {@link RoundState}를 방 전체에 브로드캐스트한다.
     * 라운드 종료 시 {@code resolveRound()} 결과(점수 델타 맵)를 추가로 브로드캐스트한다.</p>
     */
    @MessageMapping("/game/action")
    public void processAction(GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = Objects.requireNonNull(headerAccessor.getSessionAttributes());
        String nickname = (String) attrs.get("nickname");
        if (nickname == null) throw new GameException(ErrorCode.UNAUTHORIZED);

        String sessionRoomId = (String) attrs.get("roomId");
        if (sessionRoomId == null || !sessionRoomId.equals(message.roomId())) {
            throw new GameException(ErrorCode.NOT_IN_ROOM);
        }

        GameAction action = parseAction(message.type());
        PokerRoom room = findRoom(message.roomId());

        RoundState updated;
        synchronized (room) {
            if (room.getStatus() != GameStatus.IN_PROGRESS) {
                throw new GameException(ErrorCode.INVALID_TURN_PHASE);
            }
            RoundState state = room.getRoundState();
            if (state == null || state.getEndCondition() != null) {
                throw new GameException(ErrorCode.INVALID_TURN_PHASE);
            }
            updated = route(action, state, nickname, message.payload());
            room.setRoundState(updated);
            room.updateActivityTime();
            // GL-C2: 라운드 종료 확정 즉시 상태 변경 — 다른 스레드의 추가 액션 및 중복 종료 처리 차단
            if (updated.getEndCondition() != null) {
                room.setStatus(GameStatus.ROUND_END);
            }
        }

        gameBroadcastService.broadcastRoundState(message.roomId(), message.type(), nickname, updated);
        log.info("[GameController] action={} playerId={} roomId={}", action, nickname, message.roomId());

        if (updated.getEndCondition() != null) {
            finalizeRound(room, updated, message.roomId());
        }
    }

    /**
     * 라운드 종료 후처리: 점수 반영, 토너먼트 상태 동기화, 브로드캐스트 (CQ-W6)
     *
     * <p>GL-C2: 첫 번째 {@code synchronized} 에서 ROUND_END 로 전환한 스레드만 두 번째
     * {@code synchronized} 에 진입하도록 상태를 재확인한다.</p>
     */
    private void finalizeRound(PokerRoom room, RoundState updated, String roomId) {
        List<String> roundWinnerIds = roundService.determineWinners(updated);
        Map<String, Integer> scoreDelta = roundService.resolveRound(updated, roundWinnerIds);

        boolean tournamentFinished = false;
        List<String> tournamentWinners = List.of();
        Map<String, Integer> finalScores = Map.of();

        synchronized (room) {
            // GL-C2: 첫 번째 synchronized 에서 ROUND_END 로 전환한 스레드만 진입
            if (room.getStatus() != GameStatus.ROUND_END) return;
            TournamentState tournament = room.getTournamentState();
            if (tournament != null) {
                tournament = tournamentService.applyRoundResult(tournament, scoreDelta, roundWinnerIds);
                room.setTournamentState(tournament);

                // 토너먼트 탈락자를 현재 RoundState에도 반영 (PlayerStatus 동기화)
                RoundState roundState = room.getRoundState();
                if (roundState != null) {
                    tournament.getEliminatedPlayers().forEach(eliminatedId -> {
                        PlayerRoundState prs = roundState.getPlayerStates().get(eliminatedId);
                        if (prs != null && prs.getStatus() != PlayerStatus.ELIMINATED) {
                            prs.setStatus(PlayerStatus.ELIMINATED);
                        }
                    });
                }

                if (tournamentService.isFinished(tournament)) {
                    tournamentFinished = true;
                    tournamentWinners = tournamentService.getWinners(tournament);
                    finalScores = Map.copyOf(tournament.getScores());
                    room.setStatus(GameStatus.TOURNAMENT_END);
                }
            }
        }

        gameBroadcastService.broadcast(roomId, new GameMessage("ROUND_END", roomId, "SYSTEM", scoreDelta));
        log.info("[GameController] ROUND_END roomId={} endCondition={}", roomId, updated.getEndCondition());

        if (tournamentFinished) {
            gameBroadcastService.broadcast(roomId, new GameMessage("TOURNAMENT_END", roomId, "SYSTEM",
                    Map.of("winners", tournamentWinners, "scores", finalScores)));
            log.info("[GameController] TOURNAMENT_END roomId={} winners={}", roomId, tournamentWinners);
        }
    }

    // ─────────────────────────────────────────────────────────
    // private helpers
    // ─────────────────────────────────────────────────────────

    /** GameAction 이름 → enum 변환. 알 수 없는 타입이면 INVALID_TURN_PHASE 예외. */
    private GameAction parseAction(String type) {
        try {
            return GameAction.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new GameException(ErrorCode.INVALID_TURN_PHASE);
        }
    }

    /**
     * GameAction → RoundService 메서드 위임.
     * READY·CHAT 은 턴 액션이 아니므로 INVALID_TURN_PHASE 예외.
     */
    private RoundState route(GameAction action, RoundState state, String playerId, Object payload) {
        return switch (action) {
            case DRAW         -> roundService.handleDraw(state, playerId, toRequest(payload, DrawRequest.class));
            case MELD         -> roundService.handleMeld(state, playerId, toRequest(payload, MeldRequest.class));
            case EXTEND       -> roundService.handleExtend(state, playerId, toRequest(payload, ExtendRequest.class));
            case DISCARD      -> roundService.handleDiscard(state, playerId, toRequest(payload, DiscardRequest.class));
            case THANK_YOU    -> roundService.handleThankYou(state, playerId);
            case STOP         -> roundService.handleStop(state, playerId, new StopRequest());
            case DOUBT        -> roundService.handleDoubt(state, playerId, toRequest(payload, DoubtRequest.class));
            case REVEAL_BLUFF -> roundService.handleRevealBluff(state, playerId, toRequest(payload, RevealBluffRequest.class));
            default           -> throw new GameException(ErrorCode.INVALID_TURN_PHASE);
        };
    }

    /** ObjectMapper를 통해 payload(Map)를 요청 DTO 타입으로 변환한다. */
    private <T> T toRequest(Object payload, Class<T> type) {
        return objectMapper.convertValue(payload, type);
    }

    /** payload에서 firstPlayerId를 추출한다. 없으면 playerIds 첫 번째 플레이어를 반환한다. */
    @SuppressWarnings("unchecked")
    private String extractFirstPlayerId(Object payload, List<String> playerIds) {
        if (payload instanceof Map<?, ?> map) {
            Object first = map.get("firstPlayerId");
            if (first instanceof String s && !s.isBlank()) return s;
        }
        return playerIds.get(0);
    }

    private PokerRoom findRoom(String roomId) {
        return pokerRoomRepository.findById(roomId)
                .orElseThrow(() -> new GameException(ErrorCode.ROOM_NOT_FOUND));
    }
}
