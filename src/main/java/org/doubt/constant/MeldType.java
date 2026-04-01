package org.doubt.constant;

/**
 * 멜드 종류
 * SET      : 같은 숫자 3장 이상 (최대 4장)
 * STRAIGHT : 같은 무늬 연속 숫자 3장 이상 (양쪽 무제한 확장)
 * SOLO_SEVEN: 7 단독 멜드
 */
public enum MeldType {
    SET,
    STRAIGHT,
    SOLO_SEVEN
}