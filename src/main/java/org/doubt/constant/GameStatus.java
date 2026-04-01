package org.doubt.constant;

public enum GameStatus {
    WAITING,         // 플레이어 대기 중
    DEALING,         // 카드 배분 중
    IN_PROGRESS,     // 라운드 진행 중
    ROUND_END,       // 라운드 종료 (점수 계산)
    TOURNAMENT_END   // 토너먼트 종료 (최종 우승자 결정)
}
