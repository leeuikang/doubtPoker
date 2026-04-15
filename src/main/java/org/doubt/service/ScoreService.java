package org.doubt.service;

import org.doubt.constant.GameConstants;
import org.doubt.constant.Rank;
import org.doubt.constant.RoundEndCondition;
import org.doubt.dto.Card;
import org.doubt.dto.PlayerRoundState;
import org.doubt.dto.TournamentState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 점수 계산 서비스
 *
 * 배율 항목 (합산 적용):
 *   - 훌라(멜드 없이 고잉아웃): 승리 점수 4배
 *   - 스탑 선언 후 패배: 본인 점수 2배 (+1 배율)
 *   - 핸드에 7 보유: 본인 점수 2배 (+1 배율, 7은 14점)
 *   - 파산: max(본인점수*2, 다른 패배자 최고점수)
 */
@Service
public class ScoreService {

    /** 핸드에 남은 카드의 총 점수 (7은 14점) */
    public int calculateHandScore(List<Card> hand) {
        return hand.stream()
                .mapToInt(card -> card.rank() == Rank.SEVEN ? GameConstants.SEVEN_SCORE : card.rank().getBasePoint())
                .sum();
    }

    /**
     * 라운드 종료 시 각 플레이어의 점수 변화량 계산
     *
     * 배율 규칙 (합산):
     *   - 기본 1배
     *   - 스탑 선언자가 패배 시 +1 배율
     *   - 핸드에 7 보유 시 +1 배율
     *   - 파산자: 배율 대신 max(핸드점수*2, 다른 패배자 최고 조정점수) 적용
     *   - 훌라 승리자: 패배자 총합을 4배로 획득
     *
     * @param playerStates   플레이어별 라운드 상태
     * @param winnerIds      승자 ID 목록 (공동 승리 가능)
     * @param endCondition   종료 조건
     * @return playerId → 점수 델타 (양수=획득, 음수=차감)
     */
    public Map<String, Integer> calculateRoundScoreDelta(Map<String, PlayerRoundState> playerStates, List<String> winnerIds, RoundEndCondition endCondition) {
        Map<String, Integer> delta = new HashMap<>();
        playerStates.keySet().forEach(id -> delta.put(id, 0));

        // 패배자: 승자가 아닌 플레이어
        List<String> loserIds = playerStates.keySet().stream()
                .filter(id -> !winnerIds.contains(id))
                .toList();

        // 1단계: 파산자 외 패배자 조정 점수 계산
        Map<String, Integer> loserAdjustedScores = new HashMap<>();
        List<PlayerRoundState> bankruptStates = new ArrayList<>();

        for (String loserId : loserIds) {
            PlayerRoundState state = playerStates.get(loserId);
            if (state.isHasBankrupted()) {
                bankruptStates.add(state);
                continue;
            }
            int baseScore = calculateHandScore(state.getHand());
            int multiplier = 1;
            if (endCondition == RoundEndCondition.STOP && state.isHasDeclaredStop()) {
                multiplier += 1;
            }
            boolean hasSeven = state.getHand().stream().anyMatch(c -> c.rank() == Rank.SEVEN);
            if (hasSeven) multiplier += 1;

            loserAdjustedScores.put(loserId, baseScore * multiplier);
        }

        // 2단계: 파산자 조정 점수 계산 (복수 파산자 각각 독립 처리)
        // 각 파산자: max(본인 핸드점수 * 2, 다른 패배자 중 최고 조정점수)
        int maxNonBankruptScore = loserAdjustedScores.values().stream()
                .mapToInt(Integer::intValue).max().orElse(0);
        for (PlayerRoundState bankruptState : bankruptStates) {
            int bankruptBase = calculateHandScore(bankruptState.getHand());
            int ownDoubled = bankruptBase * GameConstants.BANKRUPTCY_SCORE_MULTIPLIER;
            loserAdjustedScores.put(bankruptState.getPlayerId(), Math.max(ownDoubled, maxNonBankruptScore));
        }

        // 패배자 델타 적용 (음수)
        loserAdjustedScores.forEach((id, score) -> delta.put(id, -score));

        // 3단계: 승자 획득 점수 계산
        int totalLoserScore = loserAdjustedScores.values().stream().mapToInt(Integer::intValue).sum();

        // GL-C5: 훌라 판정 — GOING_OUT이고 승자의 현재 턴 시작 시점에 테이블에 본인 멜드가 없었던 경우
        // (isHasEverMelded 사용 시 지목 성공으로 모든 멜드가 회수되어도 플래그가 잔류하는 문제 수정)
        // hadMeldsAtTurnStart 는 advanceTurn/handleThankYou 에서 턴 시작 시점에 기록됨
        boolean isHula = endCondition == RoundEndCondition.GOING_OUT && winnerIds.stream()
                .anyMatch(id -> !playerStates.get(id).isHadMeldsAtTurnStart());
        int winnerTotal = isHula ? totalLoserScore * GameConstants.HULA_MULTIPLIER : totalLoserScore;

        // 공동 승자면 균등 분배 (나머지 버림)
        if (!winnerIds.isEmpty()) {
            int perWinner = winnerTotal / winnerIds.size();
            winnerIds.forEach(id -> delta.put(id, perWinner));
        }

        return delta;
    }

    /**
     * 토너먼트 점수에 라운드 델타 반영 및 탈락자 처리
     * - 점수가 0 이하가 되면 eliminatedPlayers에 추가
     * - roundHistory에 이번 라운드 델타 기록
     *
     * @return 갱신된 TournamentState
     */
    public TournamentState applyScores(TournamentState tournament, Map<String, Integer> scoreDelta) {
        scoreDelta.forEach((playerId, delta) -> {
            int newScore = tournament.getScores().getOrDefault(playerId, 0) + delta;
            tournament.getScores().put(playerId, newScore);
        });
        tournament.getRoundHistory().add(new HashMap<>(scoreDelta));
        return tournament;
    }
}
