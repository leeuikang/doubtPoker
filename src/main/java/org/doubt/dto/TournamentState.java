package org.doubt.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 토너먼트(10판) 전체 진행 상태
 * - scores          : 플레이어별 현재 점수 (시작값 100점)
 * - eliminatedPlayers: 점수 0 이하로 탈락한 플레이어 ID 목록
 * - roundHistory    : 라운드별 점수 변화 기록
 */
@Getter
@Setter
public class TournamentState {

    private final String roomId;
    private int currentRound;
    private final int maxRounds = 10;
    private Map<String, Integer> scores;       // playerId → 현재 점수
    private List<String> eliminatedPlayers;
    private List<Map<String, Integer>> roundHistory; // 라운드별 점수 델타

    public TournamentState(String roomId) {
        this.roomId = roomId;
    }
}