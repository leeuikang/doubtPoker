package org.doubt.constant;

/**
 * 턴 내 진행 단계
 * DRAW   : 드로우 단계 (스톡 또는 버림 더미에서 1장)
 * ACTION : 멜드/확장/스탑/지목 등 액션 단계
 * DISCARD: 버리기 단계 (1장 버림 더미로)
 */
public enum TurnPhase {
    DRAW,
    ACTION,
    DISCARD
}