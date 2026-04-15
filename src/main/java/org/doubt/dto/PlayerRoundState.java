package org.doubt.dto;

import lombok.Getter;
import lombok.Setter;
import org.doubt.constant.PlayerStatus;

import java.util.List;

/**
 * 라운드 내 플레이어 상태 (매 라운드 초기화)
 * - hasMeldedThisTurn    : 현재 턴에 멜드를 1건 이상 완성했는지 (확장 가능 조건)
 * - hasEverMelded        : 이번 라운드에 멜드를 낸 적 있는지 (레거시, 현재 미사용)
 * - hadMeldsAtTurnStart  : 현재 턴 시작 시점에 테이블에 본인 멜드가 있었는지 (훌라 판정용)
 * - disconnectCount      : 연결 끊김 횟수 (반복 시 자동 패배 처리)
 */
@Getter
@Setter
public class PlayerRoundState {

    private String playerId;
    private List<Card> hand;
    private boolean hasMeldedThisTurn;
    private boolean hasEverMelded;
    private boolean hadMeldsAtTurnStart; // GL-C5: 턴 시작 시 본인 멜드 존재 여부 (훌라 판정용)
    private boolean hasDeclaredStop;  // 이번 라운드에 스탑 선언 여부 (ScoreService 배율 판정용)
    private boolean hasBankrupted;    // 파산(10장 이상) 여부 (ScoreService 배율 판정용)
    private PlayerStatus status;
    private int disconnectCount;
}