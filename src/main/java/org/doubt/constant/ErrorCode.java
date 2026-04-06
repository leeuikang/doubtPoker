package org.doubt.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 방/세션
    ROOM_NOT_FOUND("방을 찾을 수 없습니다."),
    ROOM_FULL("방이 가득 찼습니다."),
    NOT_IN_ROOM("방에 참여중이 아닙니다."),
    NOT_ENOUGH_PLAYERS("게임을 시작하려면 최소 2명이 필요합니다."),
    // 턴/액션
    INVALID_TURN("내 차례가 아닙니다."),
    INVALID_TURN_PHASE("현재 단계에서 할 수 없는 행동입니다."),
    INVALID_CARD("낼 수 없는 카드입니다."),
    // 멜드
    INVALID_MELD("유효하지 않은 멜드입니다."),
    INVALID_EXTEND("해당 멜드에 붙일 수 없는 카드입니다."),
    INVALID_BLUFF("거짓말 멜드 규칙 위반입니다."),
    MELD_NOT_FOUND("해당 멜드를 찾을 수 없습니다."),
    // 특수 선언
    CANNOT_STOP("스탑 선언 조건을 충족하지 않습니다."),
    CANNOT_DOUBT("지목할 수 없는 상태입니다."),
    CANNOT_GOING_OUT("거짓말 카드가 남아있으면 게임을 종료할 수 없습니다."),
    // 인증/인가
    UNAUTHORIZED("인증이 필요합니다."),
    TOKEN_EXPIRED("토큰이 만료되었습니다."),
    TOKEN_INVALID("유효하지 않은 토큰입니다."),
    // 서버
    INTERNAL_SERVER_ERROR("서버 에러입니다.");

    private final String message;
}
