package org.doubt.constant;

/**
 * 라운드 종료 조건
 * GOING_OUT    : 모든 카드 멜드 또는 마지막 1장 버림
 * STOP         : 핸드 점수 10점 이하 선언
 * STOCK_DEPLETED: 스톡 3회 소진
 * BANKRUPTCY   : 핸드 카드 10장 이상
 */
public enum RoundEndCondition {
    GOING_OUT,
    STOP,
    STOCK_DEPLETED,
    BANKRUPTCY
}