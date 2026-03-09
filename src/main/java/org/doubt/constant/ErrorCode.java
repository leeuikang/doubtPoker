package org.doubt.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_TURN("내 차례가 아닙니다."),
    NOT_IN_ROOM("방에 참여중이 아닙니다."),
    ROOM_FULL("방이 가득 찼습니다."),
    INVALID_CARD("낼 수 없는 카드입니다."),
    INTERNAL_SERVER_ERROR("서버 에러입니다.");

    private final String message;
}
