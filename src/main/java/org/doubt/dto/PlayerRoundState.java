package org.doubt.dto;

import lombok.Getter;
import lombok.Setter;
import org.doubt.constant.PlayerStatus;

import java.util.List;

/**
 * 라운드 내 플레이어 상태 (매 라운드 초기화)
 * - hasMeldedThisTurn    : 현재 턴에 멜드를 1건 이상 완성했는지 (확장 가능 조건)
 * - hadMeldsAtTurnStart  : 현재 턴 시작 시점에 테이블에 본인 멜드가 있었는지 (훌라 판정용)
 * - disconnectCount      : 연결 끊김 횟수 (반복 시 자동 패배 처리)
 */
@Getter
@Setter
public class PlayerRoundState {

    private String playerId;
    private List<Card> hand;
    private boolean hasMeldedThisTurn;
    private boolean hadMeldsAtTurnStart; // GL-C5: 턴 시작 시 본인 멜드 존재 여부 (훌라 판정용)
    private boolean hasDeclaredStop;  // 이번 라운드에 스탑 선언 여부 (ScoreService 배율 판정용)
    private boolean hasBankrupted;    // 파산(10장 이상) 여부 (ScoreService 배율 판정용)
    private PlayerStatus status;
    private int disconnectCount;

    /**
     * 손패만 빈 리스트로 교체한 복사본을 반환한다 (CQ-I2).
     * 상대방에게 손패를 숨길 때 사용한다.
     */
    public PlayerRoundState withMaskedHand() {
        PlayerRoundState copy = new PlayerRoundState();
        copy.playerId = this.playerId;
        copy.hand = List.of();
        copy.hasMeldedThisTurn = this.hasMeldedThisTurn;
        copy.hadMeldsAtTurnStart = this.hadMeldsAtTurnStart;
        copy.hasDeclaredStop = this.hasDeclaredStop;
        copy.hasBankrupted = this.hasBankrupted;
        copy.status = this.status;
        copy.disconnectCount = this.disconnectCount;
        return copy;
    }
}