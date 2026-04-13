package org.doubt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.GameConstants;
import org.doubt.dto.TournamentState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 토너먼트(10판) 생명주기 관리 서비스
 *
 * <h3>ruleBook §9 참조</h3>
 * <ul>
 *   <li>시작 점수: 100점</li>
 *   <li>탈락 조건: 누적 점수 0 이하</li>
 *   <li>토너먼트 종료: 10판 완료 또는 활성 플레이어 1명 이하</li>
 *   <li>다음 라운드 선 플레이어: 직전 라운드 승자</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentService {

    private final ScoreService scoreService;

    // ─────────────────────────────────────────────────────────
    // 초기화
    // ─────────────────────────────────────────────────────────

    /**
     * 토너먼트 초기화: 플레이어별 100점으로 시작, 1라운드.
     *
     * @param roomId    방 ID
     * @param playerIds 참가 플레이어 ID 목록
     */
    public TournamentState initTournament(String roomId, List<String> playerIds) {
        TournamentState tournament = new TournamentState(roomId);
        tournament.setCurrentRound(1);

        Map<String, Integer> scores = new ConcurrentHashMap<>();
        playerIds.forEach(id -> scores.put(id, GameConstants.TOURNAMENT_START_SCORE));
        tournament.setScores(scores);
        tournament.setEliminatedPlayers(new HashSet<>());
        tournament.setRoundHistory(new ArrayList<>());
        tournament.setLastRoundWinnerId(null);

        log.info("[Tournament] initialized roomId={} players={}", roomId, playerIds);
        return tournament;
    }

    // ─────────────────────────────────────────────────────────
    // 라운드 결과 반영
    // ─────────────────────────────────────────────────────────

    /**
     * 라운드 결과를 토너먼트에 반영한다.
     *
     * <ol>
     *   <li>점수 델타를 {@link ScoreService#applyScores}로 반영한다.</li>
     *   <li>winnerIds 첫 번째 플레이어를 다음 라운드 선 플레이어로 설정한다 (ruleBook §4).</li>
     *   <li>currentRound 를 1 증가시킨다.</li>
     * </ol>
     *
     * @param tournament 현재 토너먼트 상태
     * @param scoreDelta 이번 라운드 점수 변화량 (playerId → 델타)
     * @param winnerIds  이번 라운드 승자 ID 목록 (공동 우승 가능)
     * @return 갱신된 TournamentState
     */
    public TournamentState applyRoundResult(TournamentState tournament, Map<String, Integer> scoreDelta, List<String> winnerIds) {
        scoreService.applyScores(tournament, scoreDelta);

        // 점수 0 이하 탈락 처리 (ScoreService 에서 위임)
        tournament.getScores().forEach((playerId, score) -> {
            if (score <= 0) {
                eliminate(tournament, playerId);
            }
        });

        // 다음 라운드 선 플레이어 결정: winnerIds 첫 번째 플레이어 (ruleBook §4)
        if (!winnerIds.isEmpty()) {
            tournament.setLastRoundWinnerId(winnerIds.get(0));
            log.info("[Tournament] round winner(s)={} next first player={}", winnerIds, winnerIds.get(0));
        }

        tournament.setCurrentRound(tournament.getCurrentRound() + 1);
        log.info("[Tournament] round applied → currentRound={} eliminatedPlayers={}",
                tournament.getCurrentRound(), tournament.getEliminatedPlayers());
        return tournament;
    }

    /**
     * 플레이어를 토너먼트에서 탈락시킨다.
     * 이미 탈락한 경우 중복 처리하지 않는다.
     */
    public void eliminate(TournamentState tournament, String playerId) {
        if (tournament.getEliminatedPlayers().add(playerId)) {
            log.info("[Tournament] player eliminated: playerId={}", playerId);
        }
    }

    // ─────────────────────────────────────────────────────────
    // 상태 조회
    // ─────────────────────────────────────────────────────────

    /**
     * 토너먼트 종료 여부.
     * 10라운드 완료(currentRound > maxRounds) 또는 활성 플레이어가 1명 이하면 종료.
     */
    public boolean isFinished(TournamentState tournament) {
        return tournament.getCurrentRound() > tournament.getMaxRounds()
                || getActivePlayers(tournament).size() <= 1;
    }

    /**
     * 탈락하지 않은 활성 플레이어 목록.
     */
    public List<String> getActivePlayers(TournamentState tournament) {
        return tournament.getScores().keySet().stream()
                .filter(id -> !tournament.getEliminatedPlayers().contains(id))
                .toList();
    }

    /**
     * 토너먼트 우승자 목록: 활성 플레이어 중 최고 점수 보유자 (공동 가능).
     */
    public List<String> getWinners(TournamentState tournament) {
        List<String> active = getActivePlayers(tournament);
        if (active.isEmpty()) return List.of();

        int maxScore = active.stream()
                .mapToInt(id -> tournament.getScores().getOrDefault(id, 0))
                .max()
                .orElse(0);

        return active.stream()
                .filter(id -> tournament.getScores().getOrDefault(id, 0) == maxScore)
                .toList();
    }
}
