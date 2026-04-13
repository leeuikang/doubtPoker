package org.doubt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.DrawSource;
import org.doubt.constant.ErrorCode;
import org.doubt.constant.GameConstants;
import org.doubt.constant.MeldType;
import org.doubt.constant.Rank;
import org.doubt.constant.PlayerStatus;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.TurnPhase;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;
import org.doubt.dto.Meld;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        if (playerIds.size() < GameConstants.MIN_PLAYERS || playerIds.size() > GameConstants.MAX_PLAYERS) {
            throw new GameException(ErrorCode.NOT_ENOUGH_PLAYERS);
        }

        List<Card> deck = deckService.createShuffledDeck();
        Map<String, List<Card>> dealt = deckService.deal(deck, playerIds, GameConstants.INITIAL_HAND_SIZE);

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
        state.setThankYouTimerSec(GameConstants.THANK_YOU_TIMER_START_SEC);
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

        // 파산 체크: 드로우 후 핸드 10장 이상이면 해당 플레이어 즉시 탈락 (ruleBook §8.4)
        if (isBankrupt(state, playerId)) {
            playerState.setHasBankrupted(true);
            playerState.setStatus(PlayerStatus.ELIMINATED);
            log.info("[Round] BANKRUPTCY playerId={} handSize={}", playerId, playerState.getHand().size());
            checkBankruptcyEndCondition(state);
        }

        return state;
    }

    /**
     * 새 멜드 내려놓기 처리
     *
     * <p>ruleBook §3·§5.2·§6:
     * 손패에서 카드를 꺼내 SET·STRAIGHT·SOLO_SEVEN 중 하나의 멜드를 완성.
     * 거짓말 멜드 가능(최대 1장 허위), 단 거짓말 시 손패가 0장이 되는 순간 고잉아웃 불가.</p>
     */
    public RoundState handleMeld(RoundState state, String playerId, MeldRequest request) {
        validateCurrentPlayer(state, playerId);
        validatePhase(state, TurnPhase.ACTION);

        List<Card> actualCards = request.actualCards();
        List<DeclaredCard> declaredCards = request.declaredCards();
        MeldType type = request.type();

        PlayerRoundState playerState = state.getPlayerStates().get(playerId);
        validateCardsInHand(playerState.getHand(), actualCards);

        if (!meldValidationService.validateMeld(actualCards, declaredCards, type)) {
            throw new GameException(ErrorCode.INVALID_MELD);
        }

        // 거짓말 멜드 시 손패가 0장이 될 경우 고잉아웃 불가 (ruleBook §6)
        boolean bluff = isBluffMeld(actualCards, declaredCards);
        if (bluff) {
            List<Card> handAfter = handAfterRemoval(playerState.getHand(), actualCards);
            if (handAfter.isEmpty()) {
                throw new GameException(ErrorCode.CANNOT_GOING_OUT);
            }
        }

        removeCardsFromHand(playerState, actualCards);

        Meld meld = Meld.create(
                UUID.randomUUID().toString(), playerId, type,
                new ArrayList<>(actualCards), new ArrayList<>(declaredCards), bluff);
        state.getTableMelds().add(meld);
        state.setLastDoubtableMeldId(meld.getId());

        playerState.setHasMeldedThisTurn(true);
        playerState.setHasEverMelded(true);

        // 손패 소진 → 고잉아웃 (거짓말 없는 경우만, 위에서 거짓말+빈 손 차단됨)
        if (playerState.getHand().isEmpty()) {
            state.setEndCondition(RoundEndCondition.GOING_OUT);
            log.info("[Round] GOING_OUT via meld playerId={}", playerId);
        }

        return state;
    }

    /**
     * 기존 멜드 확장 처리
     *
     * <p>ruleBook §3.4·§5.2:
     * 이번 턴에 1건 이상 멜드를 완성한 플레이어만 확장 가능.
     * 확장 카드는 거짓말 불가(실제 = 선언 카드).</p>
     */
    public RoundState handleExtend(RoundState state, String playerId, ExtendRequest request) {
        validateCurrentPlayer(state, playerId);
        validatePhase(state, TurnPhase.ACTION);

        PlayerRoundState playerState = state.getPlayerStates().get(playerId);

        // 이번 턴에 1건 이상 멜드한 플레이어만 확장 가능 (ruleBook §3.4, §5.2)
        if (!playerState.isHasMeldedThisTurn()) {
            throw new GameException(ErrorCode.INVALID_EXTEND);
        }

        Meld meld = findMeldById(state, request.meldId());

        List<Card> actualCards = request.actualCards();
        List<DeclaredCard> declaredCards = request.declaredCards();

        validateCardsInHand(playerState.getHand(), actualCards);

        if (!meldValidationService.canExtend(meld, actualCards, declaredCards)) {
            throw new GameException(ErrorCode.INVALID_EXTEND);
        }

        removeCardsFromHand(playerState, actualCards);

        // 확장 카드를 extensions 맵에 누적 (동일 플레이어가 같은 멜드에 여러 번 확장 가능)
        meld.getExtensions().merge(playerId, new ArrayList<>(actualCards), (existing, added) -> {
            existing.addAll(added);
            return existing;
        });
        state.setLastDoubtableMeldId(meld.getId());

        // 확장은 거짓말 불가 → 손패 소진 시 고잉아웃
        if (playerState.getHand().isEmpty()) {
            state.setEndCondition(RoundEndCondition.GOING_OUT);
            log.info("[Round] GOING_OUT via extend playerId={}", playerId);
        }

        return state;
    }

    /**
     * 버리기 처리
     *
     * <p>ruleBook §5 DISCARD 단계:
     * 손패 1장을 버림더미 상단에 추가하고 땡큐 타이머 5초를 세팅한다.
     * 마지막 1장을 버려 손패가 0장이 되면 고잉아웃(GOING_OUT) 종료.</p>
     */
    public RoundState handleDiscard(RoundState state, String playerId, DiscardRequest request) {
        validateCurrentPlayer(state, playerId);
        validatePhase(state, TurnPhase.ACTION);

        Card card = request.card();
        PlayerRoundState playerState = state.getPlayerStates().get(playerId);
        validateCardsInHand(playerState.getHand(), List.of(card));

        playerState.getHand().remove(card);
        state.getDiscardPile().add(card); // 버림더미 상단에 push (리스트 끝 = 상단)

        // 마지막 1장 버림 → 고잉아웃
        if (playerState.getHand().isEmpty()) {
            playerState.setHasMeldedThisTurn(false);
            state.setLastDoubtableMeldId(null);
            state.setEndCondition(RoundEndCondition.GOING_OUT);
            log.info("[Round] GOING_OUT via discard playerId={}", playerId);
            return state;
        }

        // 땡큐 타이머 5초 세팅 후 다음 플레이어 턴으로 전진
        state.setThankYouTimerSec(GameConstants.THANK_YOU_TIMER_DISCARD_SEC);
        advanceTurn(state);

        return state;
    }

    /**
     * 땡큐 선언 처리
     *
     * <p>ruleBook §5.5: 버림더미에 카드가 버려진 뒤 5초(또는 라운드 시작 10초) 이내에
     * 누구나 선언 가능. 선언자가 버림더미 상단 카드를 가져와 ACTION 단계를 진행하며,
     * 원래 턴 순서(다음 플레이어)는 건너뛴다.</p>
     */
    public RoundState handleThankYou(RoundState state, String playerId) {
        if (state.getThankYouTimerSec() <= 0) {
            throw new GameException(ErrorCode.INVALID_TURN_PHASE);
        }
        PlayerRoundState playerState = state.getPlayerStates().get(playerId);
        if (playerState == null || playerState.getStatus() != PlayerStatus.ACTIVE) {
            throw new GameException(ErrorCode.INVALID_TURN);
        }
        if (state.getDiscardPile().isEmpty()) {
            throw new GameException(ErrorCode.INVALID_CARD);
        }

        // 버림더미 상단 카드 가져오기
        Card taken = state.getDiscardPile().remove(state.getDiscardPile().size() - 1);
        playerState.getHand().add(taken);

        // 선언자를 현재 플레이어로 전환 (원래 다음 플레이어 턴은 건너뜀)
        int playerIndex = state.getTurnOrder().indexOf(playerId);
        if (playerIndex < 0) {
            throw new GameException(ErrorCode.INVALID_TURN);
        }
        playerState.setHasMeldedThisTurn(false);
        state.setCurrentPlayerIndex(playerIndex);
        state.setTurnPhase(TurnPhase.ACTION);
        state.setThankYouTimerSec(0);
        state.setLastDoubtableMeldId(null);

        log.info("[Round] THANK_YOU playerId={}", playerId);
        return state;
    }

    /**
     * 스탑 선언 처리
     *
     * <p>ruleBook §8.1: 턴 시작(DRAW 전) 핸드 점수가 {STOP_SCORE_THRESHOLD}점 이하일 때만 선언 가능.
     * 모든 플레이어 핸드 공개 후 최저점 보유자 승리.</p>
     */
    public RoundState handleStop(RoundState state, String playerId, StopRequest request) {
        validateCurrentPlayer(state, playerId);
        validatePhase(state, TurnPhase.DRAW);

        PlayerRoundState playerState = state.getPlayerStates().get(playerId);
        int handScore = scoreService.calculateHandScore(playerState.getHand());
        if (handScore > GameConstants.STOP_SCORE_THRESHOLD) {
            throw new GameException(ErrorCode.CANNOT_STOP);
        }

        playerState.setHasDeclaredStop(true);
        state.setEndCondition(RoundEndCondition.STOP);
        log.info("[Round] STOP declared playerId={} score={}", playerId, handScore);
        return state;
    }

    /**
     * 거짓말 지목 처리
     *
     * <p>ruleBook §7: 자신의 턴 ACTION 단계에서 직전 멜드({@code lastDoubtableMeldId})만 지목 가능.</p>
     * <ul>
     *   <li>성공: 멜드 소유자가 actualCards 회수, 각 확장자가 extension 카드 회수,
     *       소유자 스톡에서 1장 드로우, 멜드 제거</li>
     *   <li>실패: 지목자가 스톡에서 1장 드로우</li>
     * </ul>
     */
    public RoundState handleDoubt(RoundState state, String playerId, DoubtRequest request) {
        validateCurrentPlayer(state, playerId);
        validatePhase(state, TurnPhase.ACTION);

        if (state.getLastDoubtableMeldId() == null
                || !state.getLastDoubtableMeldId().equals(request.meldId())) {
            throw new GameException(ErrorCode.CANNOT_DOUBT);
        }

        Meld meld = findMeldById(state, request.meldId());
        state.setLastDoubtableMeldId(null); // 지목 소진

        if (meld.isBluff()) {
            // 성공: 소유자 카드 회수 + 파산 체크
            PlayerRoundState ownerState = state.getPlayerStates().get(meld.getOwnerId());
            ownerState.getHand().addAll(meld.getActualCards());
            checkCardRecoveryBankruptcy(state, meld.getOwnerId(), ownerState);

            // 확장자 카드 회수 + 파산 체크
            meld.getExtensions().forEach((extenderId, cards) -> {
                PlayerRoundState extenderState = state.getPlayerStates().get(extenderId);
                extenderState.getHand().addAll(cards);
                checkCardRecoveryBankruptcy(state, extenderId, extenderState);
            });

            state.getTableMelds().remove(meld);

            // 소유자가 이미 파산하지 않은 경우에만 패널티 드로우
            if (ownerState.getStatus() != PlayerStatus.ELIMINATED) {
                drawOneFromStock(state, meld.getOwnerId());
            }
            log.info("[Round] DOUBT success meldId={} owner={}", meld.getId(), meld.getOwnerId());
        } else {
            // 실패: 지목자 1장 드로우
            drawOneFromStock(state, playerId);
            log.info("[Round] DOUBT failure meldId={} doubter={}", meld.getId(), playerId);
        }

        return state;
    }

    /**
     * 거짓말 자진 공개 처리
     *
     * <p>ruleBook §7: 본인 턴에 자신이 낸 거짓말 멜드를 공개 가능.
     * 소유자는 카드를 회수하고, 확장자는 카드 회수 없이 1장 드로우 패널티만 받는다.
     * (카드 회수는 지목 성공 시에만 해당)</p>
     */
    public RoundState handleRevealBluff(RoundState state, String playerId, RevealBluffRequest request) {
        validateCurrentPlayer(state, playerId);
        validatePhase(state, TurnPhase.ACTION);

        Meld meld = findMeldById(state, request.meldId());

        if (!meld.getOwnerId().equals(playerId)) {
            throw new GameException(ErrorCode.INVALID_MELD);
        }
        if (!meld.isBluff()) {
            throw new GameException(ErrorCode.INVALID_BLUFF);
        }

        // 소유자 카드 회수
        state.getPlayerStates().get(playerId).getHand().addAll(meld.getActualCards());

        // 확장자: 1장 드로우 패널티만 (ruleBook §7 — 카드 회수는 지목 성공 시에만 해당)
        meld.getExtensions().forEach((extenderId, cards) ->
                drawOneFromStock(state, extenderId));

        state.getTableMelds().remove(meld);
        state.setLastDoubtableMeldId(null); // 자진 공개는 지목 가능 대상 초기화 (직전 멜드 체인 단절)

        log.info("[Round] REVEAL_BLUFF playerId={} meldId={}", playerId, request.meldId());
        return state;
    }

    /**
     * 턴 타임아웃 자동 처리
     *
     * <p>ruleBook §5: 20초 초과 시 자동 드로우 후 7 제외 최고 점수 카드 버림.</p>
     * <ol>
     *   <li>이미 종료된 라운드이거나 해당 플레이어가 ACTIVE 가 아니면 무시.</li>
     *   <li>DRAW 페이즈면 스톡에서 자동 드로우(파산 체크 포함).</li>
     *   <li>7을 제외한 최고 점수 카드를 버림더미에 추가하고 턴 전진.</li>
     *   <li>버림으로 손패가 빈 경우 GOING_OUT 처리.</li>
     * </ol>
     */
    public RoundState handleTurnTimeout(RoundState state, String playerId) {
        if (state.getEndCondition() != null) return state;

        PlayerRoundState playerState = state.getPlayerStates().get(playerId);
        if (playerState == null || playerState.getStatus() != PlayerStatus.ACTIVE) return state;

        // DRAW 페이즈: 스톡에서 자동 드로우
        if (state.getTurnPhase() == TurnPhase.DRAW) {
            if (state.getStockPile().isEmpty()) {
                refillStock(state);
                if (state.getEndCondition() != null) return state;
            }
            if (!state.getStockPile().isEmpty()) {
                Card drawn = state.getStockPile().remove(0);
                playerState.getHand().add(drawn);
                state.setTurnPhase(TurnPhase.ACTION);
                if (isBankrupt(state, playerId)) {
                    playerState.setHasBankrupted(true);
                    playerState.setStatus(PlayerStatus.ELIMINATED);
                    log.info("[Round] BANKRUPTCY (timeout draw) playerId={}", playerId);
                    checkBankruptcyEndCondition(state);
                    if (state.getEndCondition() != null) return state;
                }
            }
        }

        // 7 제외 최고 점수 카드 자동 버림
        Card toDiscard = selectAutoDiscardCard(playerState.getHand());
        if (toDiscard == null) return state;

        playerState.getHand().remove(toDiscard);
        state.getDiscardPile().add(toDiscard);

        if (playerState.getHand().isEmpty()) {
            state.setEndCondition(RoundEndCondition.GOING_OUT);
            log.info("[Round] GOING_OUT via timeout discard playerId={}", playerId);
            return state;
        }

        state.setThankYouTimerSec(GameConstants.THANK_YOU_TIMER_DISCARD_SEC);
        advanceTurn(state);
        log.info("[Round] TURN_TIMEOUT playerId={} discarded={}", playerId, toDiscard);
        return state;
    }

    /**
     * 자동 버릴 카드 선택: 7 제외 최고 점수 카드.
     * 손패가 모두 7이면 첫 번째 카드를 반환한다.
     */
    private Card selectAutoDiscardCard(List<Card> hand) {
        if (hand.isEmpty()) return null;
        return hand.stream()
                .filter(c -> c.rank() != Rank.SEVEN)
                .max(Comparator.comparingInt(c -> c.rank().getBasePoint()))
                .orElse(hand.get(0));
    }

    // ----------------------------------------------------------------
    // private helpers
    // ----------------------------------------------------------------

    /**
     * 스톡 재구성: 버림더미 상단 카드를 제외한 나머지를 섞어 새 스톡 구성.
     * stockRefillCount >= 2 이면 3번째 소진 → STOCK_DEPLETED 종료.
     */
    private void refillStock(RoundState state) {
        if (state.getStockRefillCount() >= GameConstants.MAX_STOCK_REFILLS) {
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

    /**
     * 라운드 종료 처리: 승자 결정 후 ScoreService 에 점수 델타 계산을 위임한다.
     *
     * <p>endCondition 이 null 이 아닌 상태에서 호출해야 한다.</p>
     *
     * @return playerId → 점수 변화량 (양수=획득, 음수=차감)
     */
    public Map<String, Integer> resolveRound(RoundState state, List<String> winnerIds) {
        log.info("[Round] resolved endCondition={} winners={}", state.getEndCondition(), winnerIds);
        return scoreService.calculateRoundScoreDelta(
                state.getPlayerStates(), winnerIds, state.getEndCondition());
    }

    /** endCondition 별 승자 ID 목록 결정 */
    public List<String> determineWinners(RoundState state) {
        return switch (state.getEndCondition()) {
            case GOING_OUT            -> findGoingOutWinners(state);
            case STOP, STOCK_DEPLETED -> findLowestScoreWinners(state);
            case BANKRUPTCY           -> findActiveWinners(state);
        };
    }

    /** GOING_OUT: 손패가 빈 ACTIVE 플레이어 */
    private List<String> findGoingOutWinners(RoundState state) {
        return state.getPlayerStates().entrySet().stream()
                .filter(e -> e.getValue().getHand().isEmpty()
                        && e.getValue().getStatus() == PlayerStatus.ACTIVE)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** STOP / STOCK_DEPLETED: ACTIVE 플레이어 중 핸드 점수 최솟값 보유자 (공동 가능) */
    private List<String> findLowestScoreWinners(RoundState state) {
        Map<String, Integer> scores = state.getPlayerStates().entrySet().stream()
                .filter(e -> e.getValue().getStatus() == PlayerStatus.ACTIVE)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> scoreService.calculateHandScore(e.getValue().getHand())));
        if (scores.isEmpty()) return List.of();
        int minScore = Collections.min(scores.values());
        return scores.entrySet().stream()
                .filter(e -> e.getValue() == minScore)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** BANKRUPTCY: 파산 후 남은 ACTIVE 플레이어 전원 승자 */
    private List<String> findActiveWinners(RoundState state) {
        return state.getPlayerStates().entrySet().stream()
                .filter(e -> e.getValue().getStatus() == PlayerStatus.ACTIVE)
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * 파산 후 활성 플레이어 수를 확인해 1명 이하면 BANKRUPTCY 종료 조건을 설정한다.
     * 이미 다른 endCondition 이 설정된 경우에는 덮어쓰지 않는다.
     */
    private void checkBankruptcyEndCondition(RoundState state) {
        if (state.getEndCondition() != null) return;
        long activeCount = state.getPlayerStates().values().stream()
                .filter(p -> p.getStatus() == PlayerStatus.ACTIVE)
                .count();
        if (activeCount <= 1) {
            state.setEndCondition(RoundEndCondition.BANKRUPTCY);
            log.info("[Round] BANKRUPTCY end: {} active player(s) remain", activeCount);
        }
    }

    /** 파산 여부 체크 (핸드 10장 이상이면 즉시 탈락) */
    private boolean isBankrupt(RoundState state, String playerId) {
        return state.getPlayerStates().get(playerId).getHand().size() >= GameConstants.BANKRUPTCY_HAND_SIZE;
    }

    /**
     * 카드 회수 직후 파산 기준을 충족하면 즉시 탈락 처리한다.
     * 이미 ELIMINATED 상태인 경우에는 중복 처리하지 않는다.
     */
    private void checkCardRecoveryBankruptcy(RoundState state, String playerId, PlayerRoundState playerState) {
        if (playerState.getStatus() == PlayerStatus.ELIMINATED) return;
        if (isBankrupt(state, playerId)) {
            playerState.setHasBankrupted(true);
            playerState.setStatus(PlayerStatus.ELIMINATED);
            log.info("[Round] BANKRUPTCY (card recovery) playerId={}", playerId);
            checkBankruptcyEndCondition(state);
        }
    }

    /** 플레이어 손패에 required 카드가 모두 있는지 확인 (없으면 INVALID_CARD) */
    private void validateCardsInHand(List<Card> hand, List<Card> required) {
        List<Card> handCopy = new ArrayList<>(hand);
        for (Card card : required) {
            if (!handCopy.remove(card)) {
                throw new GameException(ErrorCode.INVALID_CARD);
            }
        }
    }

    /** 손패에서 cards를 1장씩 제거 (이미 validateCardsInHand 를 통과한 카드만 전달) */
    private void removeCardsFromHand(PlayerRoundState playerState, List<Card> cards) {
        List<Card> hand = playerState.getHand();
        for (Card card : cards) {
            hand.remove(card);
        }
    }

    /** cards 를 제거했을 때 남는 손패를 반환 (원본 불변) */
    private List<Card> handAfterRemoval(List<Card> hand, List<Card> toRemove) {
        List<Card> copy = new ArrayList<>(hand);
        toRemove.forEach(copy::remove);
        return copy;
    }

    /** 실제 카드와 선언 카드 중 1장이라도 다르면 거짓말 멜드 */
    private boolean isBluffMeld(List<Card> actualCards, List<DeclaredCard> declaredCards) {
        for (int i = 0; i < actualCards.size(); i++) {
            Card actual = actualCards.get(i);
            DeclaredCard declared = declaredCards.get(i);
            if (actual.rank() != declared.declaredRank() || actual.suit() != declared.declaredSuit()) {
                return true;
            }
        }
        return false;
    }

    /** meldId 로 테이블 멜드를 찾아 반환 (없으면 MELD_NOT_FOUND) */
    private Meld findMeldById(RoundState state, String meldId) {
        return state.getTableMelds().stream()
                .filter(m -> meldId.equals(m.getId()))
                .findFirst()
                .orElseThrow(() -> new GameException(ErrorCode.MELD_NOT_FOUND));
    }

    private void validateCurrentPlayer(RoundState state, String playerId) {
        String currentPlayerId = state.getTurnOrder().get(state.getCurrentPlayerIndex());
        if (!currentPlayerId.equals(playerId)) {
            throw new GameException(ErrorCode.INVALID_TURN);
        }
        PlayerRoundState playerState = state.getPlayerStates().get(playerId);
        if (playerState == null || playerState.getStatus() != PlayerStatus.ACTIVE) {
            throw new GameException(ErrorCode.INVALID_TURN);
        }
    }

    private void validatePhase(RoundState state, TurnPhase expected) {
        if (state.getTurnPhase() != expected) {
            throw new GameException(ErrorCode.INVALID_TURN_PHASE);
        }
    }

    /**
     * 스톡에서 1장을 드로우해 지정 플레이어에게 지급한다. (패널티 드로우 전용)
     * 스톡 소진 시 refillStock 을 시도하며, 그래도 비어있으면 드로우를 건너뛴다.
     * 드로우 후 파산 기준을 충족하면 즉시 탈락 처리한다.
     */
    private void drawOneFromStock(RoundState state, String playerId) {
        if (state.getStockPile().isEmpty()) {
            refillStock(state);
            if (state.getEndCondition() != null) return; // STOCK_DEPLETED
        }
        if (state.getStockPile().isEmpty()) return;

        Card drawn = state.getStockPile().remove(0);
        PlayerRoundState playerState = state.getPlayerStates().get(playerId);
        playerState.getHand().add(drawn);

        if (isBankrupt(state, playerId)) {
            playerState.setHasBankrupted(true);
            playerState.setStatus(PlayerStatus.ELIMINATED);
            log.info("[Round] BANKRUPTCY (penalty draw) playerId={}", playerId);
            checkBankruptcyEndCondition(state);
        }
    }

    /**
     * 다음 ACTIVE 플레이어로 턴을 전진한다.
     * ELIMINATED 플레이어는 건너뛰고, 현재 플레이어의 hasMeldedThisTurn 을 리셋한다.
     */
    private void advanceTurn(RoundState state) {
        String currentPlayerId = state.getTurnOrder().get(state.getCurrentPlayerIndex());
        state.getPlayerStates().get(currentPlayerId).setHasMeldedThisTurn(false);

        List<String> turnOrder = state.getTurnOrder();
        int size = turnOrder.size();
        int nextIndex = (state.getCurrentPlayerIndex() + 1) % size;

        for (int i = 0; i < size; i++) {
            String nextPlayerId = turnOrder.get(nextIndex);
            if (state.getPlayerStates().get(nextPlayerId).getStatus() == PlayerStatus.ACTIVE) {
                break;
            }
            nextIndex = (nextIndex + 1) % size;
        }

        state.setCurrentPlayerIndex(nextIndex);
        state.setTurnPhase(TurnPhase.DRAW);
        state.setLastDoubtableMeldId(null);
        log.info("[Round] turn advanced to playerId={}", turnOrder.get(nextIndex));
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
