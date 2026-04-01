package org.doubt.dto.request;

/**
 * 스탑 선언 요청
 * 조건: 턴 시작 시 핸드 점수 10점 이하
 * 모든 플레이어 핸드 공개 후 최저점 보유자 승리
 */
public record StopRequest() {
}