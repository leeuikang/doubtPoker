package org.doubt.constant;

/**
 * 플레이어 상태
 * ACTIVE       : 정상 참여 중
 * DISCONNECTED : 연결 끊김 (재접속 대기)
 * AI_CONTROLLED: AI가 대체 진행 중
 * ELIMINATED   : 점수 0 이하로 탈락
 */
public enum PlayerStatus {
    ACTIVE,
    DISCONNECTED,
    AI_CONTROLLED,
    ELIMINATED
}