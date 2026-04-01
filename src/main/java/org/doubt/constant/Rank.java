package org.doubt.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 카드 랭크. 순환: A-2-3-...-K-A
 * 점수: A=1, 2~6·8~10=표시값, 7=핸드에 남으면 14점, J=11, Q=12, K=13
 */
@Getter
@RequiredArgsConstructor
public enum Rank {
    ACE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),   // 핸드에 남을 경우 14점 (ScoreService에서 처리)
    EIGHT(8),
    NINE(9),
    TEN(10),
    JACK(11),
    QUEEN(12),
    KING(13);

    private final int basePoint;
}