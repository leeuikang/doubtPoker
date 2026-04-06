package org.doubt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.DrawSource;
import org.doubt.constant.ErrorCode;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.doubt.dto.request.DiscardRequest;
import org.doubt.dto.request.DoubtRequest;
import org.doubt.dto.request.DrawRequest;
import org.doubt.dto.request.ExtendRequest;
import org.doubt.dto.request.MeldRequest;
import org.doubt.dto.request.RevealBluffRequest;
import org.doubt.dto.request.StopRequest;
import org.doubt.exception.GameException;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 라운드 진행 오케스트레이션 서비스
 * 턴 순서, 액션 위임, 종료 조건 체크를 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoundService {

    private final DeckService deckService;
    private final MeldValidationService meldValidationService;
    private final ScoreService scoreService;

    /**
     * 새 라운드 시작: 덱 배분, 초기 RoundState 반환
     *
     * <p>ruleBook §4: 플레이어별 7장 배분, 스톡 구성, 초기 버림더미 카드 세팅,
     * 10초 땡큐 타이머 시작.</p>
     *
     * @param roomId       방 ID
     * @param playerIds    참가 플레이어 ID 목록 (2~5명)
     * @param firstPlayerId 선 플레이어 ID (반드시 playerIds 에 포함)
     */
    public RoundState startRound(String roomId, List<String> playerIds, String firstPlayerId) {
        if (playerIds.size() < 2 || playerIds.size() > 5) {
            throw new GameException(ErrorCode.NOT_ENOUGH_PLAYERS);
        }

        List<Card> deck = deckService.createShuffledDeck();
        Map<String, List<Card>> dealt = deckService.deal(deck, playerIds, 7);

        // 스톡 구성 및 초기 버림더미 카드 1장 세팅
        List<Card> stockPile = new ArrayList<>(dealt.get("STOCK"));
        List<Card> discardPile = new ArrayList<>();
        discardPile.add(stockPile.remove(0));

        // 턴 순서: firstPlayerId 부터 시계 방향
        List<String> turnOrder = buildTurnOrder(playerIds, firstPlayerId);

        // 플레이어 상태 초기화
        Map<String, PlayerRoundState> playerStates = new LinkedHashMap<>();
        for (String playerId : playerIds) {
            PlayerRoundState prs = new PlayerRoundState();
            prs.setPlayerId(playerId);
            prs.setHand(new ArrayList<>(dealt.get(playerId)));
            prs.setHasMeldedThisTurn(false);
            prs.setHasEverMelded(false);
            prs.setHasDeclaredStop(false);
            prs.setHasBankrupted(false);
            prs.setStatus(PlayerStatus.ACTIVE);
            prs.setDisconnectCount(0);
            playerStates.put(playerId, prs);
        }

        RoundState state = new RoundState();
        state.setStockPile(stockPile);
        state.setDiscardPile(discardPile);
        state.setTableMelds(new ArrayList<>());
        state.setPlayerStates(playerStates);
        state.setTurnOrder(turnOrder);
        state.setCurrentPlayerIndex(0);
        state.setTurnPhase(TurnPhase.DRAW);
        state.setStockRefillCount(0);
        state.setLastDoubtableMeldId(null);
        state.setThankYouTimerSec(10); // 초기 버림더미 세팅 후 10초 땡큐 타이머
        state.setEndCondition(null);

        log.info("[Round] started roomId={} players={} first={}", roomId, playerIds, firstPlayerId);
        return state;
    }

    /**
     * 드로우 처리
     *
     * <p>ruleBook §5.1: 스톡 또는 버림더미 상단에서 1장 드로우.</p>
     * <p>ruleBook §4: 스톡 소진 시 버림더미 상단 1장 제외 후 재구성(최대 2회),
     * 3번째 소진 시 즉시 STOCK_DEPLETED 종료.</p>
     */
    public RoundState handleDraw(RoundState state, String playerId, DrawRequest request) {
        validateCurrentPlayer(state, playerId);
        validatePhase(state, TurnPhase.DRAW);

        Card drawn;
        if (request.source() == DrawSource.STOCK) {
            if (state.getStockPile().isEmpty()) {
                refillStock(state);
                if (state.getEndCondition() != null) {
                    return state; // 3번째 소진 → 즉시 종료
                }
            }
            // 재구성 후에도 스톡이 비어있으면(버림더미 카드가 1장뿐인 극단적 상황) 종료
            if (state.getStockPile().isEmpty()) {
                state.setEndCondition(RoundEndCondition.STOCK_DEPLETED);
                return state;
            }
            drawn = state.getStockPile().remove(0);
        } else {
            // 버림더미 상단 드로우
            if (state.getDiscardPile().isEmpty()) {
                throw new GameException(ErrorCode.INVALID_CARD);
            }
            drawn = state.getDiscardPile().remove(state.getDiscardPile().size() - 1);
        }

        PlayerRoundState playerState = state.getPlayerStates().get(playerId);
        playerState.getHand().add(drawn);

        state.setTurnPhase(TurnPhase.ACTION);

        // 파산 체크: 드로우 후 핸드 10장 이상이면 해당 플레이어만 즉시 탈락 (ruleBook §8.4)
        // 라운드 전체 종료 여부는 item 9(종료 조건) 구현 시 활성 플레이어 수 기반으로 판단
        if (isBankrupt(state, playerId)) {
            playerState.setHasBankrupted(true);
            playerState.setStatus(PlayerStatus.ELIMINATED);
            log.info("[Round] BANKRUPTCY playerId={} handSize={}", playerId, playerState.getHand().size());
        }

        return state;
    }

    /** 새 멜드 내려놓기 처리 */
    public RoundState handleMeld(RoundState state, String playerId, MeldRequest request) {
        return null;
    }

    /** 기존 멜드 확장 처리 */
    public RoundState handleExtend(RoundState state, String playerId, ExtendRequest request) {
        return null;
    }

    /** 버리기 처리 (버린 후 땡큐 타이머 5초 시작) */
    public RoundState handleDiscard(RoundState state, String playerId, DiscardRequest request) {
        return null;
    }

    /** 땡큐 선언 처리 */
    public RoundState handleThankYou(RoundState state, String playerId) {
        return null;
    }

    /** 스탑 선언 처리 */
    public RoundState handleStop(RoundState state, String playerId, StopRequest request) {
        return null;
    }

    /** 거짓말 지목 처리 */
    public RoundState handleDoubt(RoundState state, String playerId, DoubtRequest request) {
        return null;
    }

    /** 거짓말 자진 공개 처리 */
    public RoundState handleRevealBluff(RoundState state, String playerId, RevealBluffRequest request) {
        return null;
    }

    /** 턴 타임아웃 처리 (20초 초과: 드로우 후 가장 높은 카드 버림) */
    public RoundState handleTurnTimeout(RoundState state, String playerId) {
        return null;
    }

    // ----------------------------------------------------------------
    // private helpers
    // ----------------------------------------------------------------

    /**
     * 스톡 재구성: 버림더미 상단 카드를 제외한 나머지를 섞어 새 스톡 구성.
     * stockRefillCount >= 2 이면 3번째 소진 → STOCK_DEPLETED 종료.
     */
    private void refillStock(RoundState state) {
        if (state.getStockRefillCount() >= 2) {
            state.setEndCondition(RoundEndCondition.STOCK_DEPLETED);
            log.info("[Round] STOCK_DEPLETED: 3rd stock depletion");
            return;
        }
        List<Card> discard = state.getDiscardPile();
        Card topDiscard = discard.get(discard.size() - 1);
        List<Card> newStock = new ArrayList<>(discard.subList(0, discard.size() - 1));
        Collections.shuffle(newStock);
        state.setStockPile(newStock);
        state.setDiscardPile(new ArrayList<>(List.of(topDiscard)));
        state.setStockRefillCount(state.getStockRefillCount() + 1);
        log.info("[Round] stock refilled count={}", state.getStockRefillCount());
    }

    /** 파산 여부 체크 (핸드 10장 이상이면 즉시 탈락) */
    private boolean isBankrupt(RoundState state, String playerId) {
        return state.getPlayerStates().get(playerId).getHand().size() >= 10;
    }

    private void validateCurrentPlayer(RoundState state, String playerId) {
        String currentPlayerId = state.getTurnOrder().get(state.getCurrentPlayerIndex());
        if (!currentPlayerId.equals(playerId)) {
            throw new GameException(ErrorCode.INVALID_TURN);
        }
    }

    private void validatePhase(RoundState state, TurnPhase expected) {
        if (state.getTurnPhase() != expected) {
            throw new GameException(ErrorCode.INVALID_TURN_PHASE);
        }
    }

    private List<String> buildTurnOrder(List<String> playerIds, String firstPlayerId) {
        int startIndex = playerIds.indexOf(firstPlayerId);
        if (startIndex < 0) {
            throw new GameException(ErrorCode.INVALID_TURN);
        }
        List<String> turnOrder = new ArrayList<>(playerIds.size());
        for (int i = 0; i < playerIds.size(); i++) {
            turnOrder.add(playerIds.get((startIndex + i) % playerIds.size()));
        }
        return turnOrder;
    }
}
