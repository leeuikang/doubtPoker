package org.doubt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.DrawSource;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.Rank;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.RoundState;
import org.doubt.dto.request.DiscardRequest;
import org.doubt.dto.request.DrawRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * AI 플레이어 서비스 (연결 끊긴 플레이어 대체)
 *
 * AI 행동 규칙:
 *   - 땡큐 선언 안 함
 *   - 거짓말 지목 안 함
 *   - 스탑 선언 안 함
 *   - 매 턴: 드로우 후 7 제외 가장 높은 점수의 카드 버림
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final RoundService roundService;

    /**
     * AI 턴 자동 실행
     *
     * <ol>
     *   <li>이미 종료된 라운드이거나 해당 플레이어가 ACTIVE가 아니면 무시.</li>
     *   <li>DRAW 페이즈면 스톡에서 자동 드로우.</li>
     *   <li>드로우 후 라운드가 종료되었으면(파산 등) 그대로 반환.</li>
     *   <li>7 제외 최고 점수 카드를 버린다. 손패가 모두 7이면 첫 번째 카드를 버린다.</li>
     * </ol>
     *
     * @param state    현재 라운드 상태
     * @param aiPlayer AI가 대체하는 플레이어 상태
     * @return 액션 처리 후 갱신된 RoundState
     */
    public RoundState takeTurn(RoundState state, PlayerRoundState aiPlayer) {
        if (state.getEndCondition() != null) return state;

        String playerId = aiPlayer.getPlayerId();
        if (aiPlayer.getStatus() != PlayerStatus.ACTIVE) {
            log.info("[AI] skipped — player not active playerId={}", playerId);
            return state;
        }

        // DRAW 페이즈: 스톡에서 자동 드로우
        if (state.getTurnPhase() == TurnPhase.DRAW) {
            state = roundService.handleDraw(state, playerId, new DrawRequest(DrawSource.STOCK));
            if (state.getEndCondition() != null) return state;
        }

        // ACTION 페이즈: 7 제외 최고 점수 카드 버림
        PlayerRoundState current = state.getPlayerStates().get(playerId);
        Card toDiscard = selectDiscardCard(current.getHand());
        if (toDiscard == null) return state;

        log.info("[AI] takeTurn playerId={} discarding={}", playerId, toDiscard);
        return roundService.handleDiscard(state, playerId, new DiscardRequest(toDiscard));
    }

    /** 7 제외 최고 점수 카드 선택. 손패가 모두 7이면 첫 번째 카드를 반환한다. */
    private Card selectDiscardCard(List<Card> hand) {
        if (hand.isEmpty()) return null;
        return hand.stream()
                .filter(c -> c.rank() != Rank.SEVEN)
                .max(Comparator.comparingInt(c -> c.rank().getBasePoint()))
                .orElse(hand.get(0));
    }
}
