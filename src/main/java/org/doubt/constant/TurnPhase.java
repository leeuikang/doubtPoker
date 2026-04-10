package org.doubt.constant;

/**
 * 턴 내 진행 단계
 * DRAW   : 드로우 단계 (스톡 또는 버림 더미에서 1장)
 * ACTION : 멜드/확장/스탑/지목/버리기 등 액션 단계
 *          버리기(handleDiscard)는 ACTION 단계의 마지막 행동으로 처리되며
 *          별도의 DISCARD 단계는 존재하지 않는다.
 */
public enum TurnPhase {
    DRAW,
    ACTION
}
