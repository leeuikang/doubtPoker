package org.doubt.constant;

public enum GameAction {
    // 턴 액션
    DRAW,           // 스톡/버림 더미에서 드로우
    MELD,           // 새 멜드 내려놓기
    EXTEND,         // 기존 멜드에 카드 추가
    DISCARD,        // 카드 버리기
    // 특수 선언
    THANK_YOU,      // 땡큐 선언
    STOP,           // 스탑 선언 (핸드 ≤ 10점)
    DOUBT,          // 거짓말 지목
    REVEAL_BLUFF,   // 거짓말 자진 공개
    // 로비
    READY,
    CHAT
}
