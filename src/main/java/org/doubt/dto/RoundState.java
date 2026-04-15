package org.doubt.dto;

import lombok.Getter;
import lombok.Setter;
import org.doubt.constant.RoundEndCondition;
import org.doubt.constant.TurnPhase;

import java.util.List;
import java.util.Map;

/**
 * 라운드 진행 상태 (서버 측 정보, 매 라운드 초기화)
 * - stockRefillCount : 스톡 재구성 횟수 (최대 2회, 3회 시 즉시 종료)
 * - lastDoubtableMeld: 지목 가능한 직전 멜드 ID (턴이 바뀌면 갱신)
 * - thankYouTimerSec : 활성화된 땡큐 타이머 남은 초 (0이면 비활성)
 */
@Getter
@Setter
public class RoundState {

    private List<Card> stockPile;
    private int stockPileSize; // 클라이언트 전송용 크기 (마스킹 시 stockPile 대신 사용)
    private List<Card> discardPile;
    private List<Meld> tableMelds;
    private Map<String, PlayerRoundState> playerStates; // playerId → 상태
    private List<String> turnOrder;  // 시계 방향 플레이어 ID 순서
    private int currentPlayerIndex;
    private TurnPhase turnPhase;
    private int stockRefillCount;
    private String lastDoubtableMeldId;
    private String lastDiscardPlayerId;     // 직전 버리기를 수행한 플레이어 (땡큐 자기 회수 방지)
    private int thankYouTimerSec;
    private RoundEndCondition endCondition; // null이면 진행 중
}