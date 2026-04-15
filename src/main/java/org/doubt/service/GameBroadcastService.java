package org.doubt.service;

import lombok.RequiredArgsConstructor;
import org.doubt.dto.Card;
import org.doubt.dto.GameMessage;
import org.doubt.dto.Meld;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.doubt.handler.SessionManager;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 게임 메시지 브로드캐스트 서비스 (S-5)
 *
 * <h3>RoundState 마스킹</h3>
 * <p>수신자 본인의 손패만 볼 수 있도록 타 플레이어의 {@link PlayerRoundState#getHand()}를
 * 빈 리스트로 교체한 뒤 개인 토픽으로 전송한다.</p>
 *
 * <ul>
 *   <li>일반 메시지: {@code /topic/room/{roomId}}</li>
 *   <li>RoundState 메시지: {@code /topic/room/{roomId}/player/{nickname}}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GameBroadcastService {

    private final SimpMessageSendingOperations messagingTemplate;
    private final SessionManager sessionManager;

    /** 일반 메시지를 방 전체 토픽으로 브로드캐스트한다. */
    public void broadcast(String roomId, GameMessage message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }

    /**
     * RoundState 를 수신자별로 마스킹하여 개인 토픽으로 전송한다.
     *
     * <p>현재 방의 세션 목록을 조회해 각 플레이어에게 손패가 마스킹된 RoundState 를 전송한다.
     * 수신자 본인의 손패는 그대로 유지하고, 나머지 플레이어의 손패는 빈 리스트로 교체한다.</p>
     */
    public void broadcastRoundState(String roomId, String type, String sender, RoundState state) {
        for (String nickname : sessionManager.getUserList(roomId)) {
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId + "/player/" + nickname,
                    new GameMessage(type, roomId, sender, maskHandsExcept(state, nickname))
            );
        }
    }

    /**
     * viewerNickname 본인 손패를 제외한 모든 플레이어의 손패를 빈 리스트로 교체한 RoundState 사본을 반환한다.
     */
    private RoundState maskHandsExcept(RoundState state, String viewerNickname) {
        RoundState masked = new RoundState();
        masked.setStockPile(null); // 드로우 예측 방지: 카드 값은 숨기고 크기만 전달
        masked.setStockPileSize(state.getStockPile() != null ? state.getStockPile().size() : 0);
        masked.setDiscardPile(state.getDiscardPile());
        masked.setTableMelds(maskMeldsFor(state.getTableMelds(), viewerNickname));
        masked.setTurnOrder(state.getTurnOrder());
        masked.setCurrentPlayerIndex(state.getCurrentPlayerIndex());
        masked.setTurnPhase(state.getTurnPhase());
        masked.setStockRefillCount(state.getStockRefillCount());
        masked.setLastDoubtableMeldId(state.getLastDoubtableMeldId());
        masked.setThankYouTimerSec(state.getThankYouTimerSec());
        masked.setEndCondition(state.getEndCondition());

        Map<String, PlayerRoundState> maskedPlayerStates = new HashMap<>();
        state.getPlayerStates().forEach((playerId, prs) -> {
            if (playerId.equals(viewerNickname)) {
                maskedPlayerStates.put(playerId, prs);
            } else {
                maskedPlayerStates.put(playerId, prs.withMaskedHand());
            }
        });
        masked.setPlayerStates(maskedPlayerStates);
        return masked;
    }

    /**
     * 비소유자에게 실제 카드(actualCards)와 거짓말 여부(isBluff)를 숨긴 멜드 목록을 반환한다.
     * 멜드 소유자에게는 원본 인스턴스를 그대로 반환한다.
     */
    private List<Meld> maskMeldsFor(List<Meld> melds, String viewerNickname) {
        if (melds == null) return null;
        return melds.stream().map(meld -> {
            if (viewerNickname.equals(meld.getOwnerId())) {
                return meld;
            }
            Meld masked = Meld.create(
                    meld.getId(),
                    meld.getOwnerId(),
                    meld.getType(),
                    List.of(),
                    meld.getDeclaredCards(),
                    false
            );
            // SEC-M5: 비소유자에게 extensions 실제 카드 노출 방지 — 키(확장자 ID)는 유지, 값은 빈 리스트로 교체
            Map<String, List<Card>> extensionsCopy = new LinkedHashMap<>();
            meld.getExtensions().forEach((extenderId, cards) ->
                    extensionsCopy.put(extenderId, List.of()));
            masked.setExtensions(extensionsCopy);
            return masked;
        }).toList();
    }
}
