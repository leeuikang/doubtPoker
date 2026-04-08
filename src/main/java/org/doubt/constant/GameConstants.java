package org.doubt.constant;

/** 게임 규칙에서 사용하는 숫자 상수 모음 */
public final class GameConstants {

    private GameConstants() {}

    // ----------------------------------------------------------------
    // 타이머
    // ----------------------------------------------------------------

    /** 라운드 시작 시 초기 버림더미 세팅 후 땡큐 타이머 (초) */
    public static final int THANK_YOU_TIMER_START_SEC = 10;

    /** 버리기 직후 땡큐 타이머 (초) */
    public static final int THANK_YOU_TIMER_DISCARD_SEC = 5;

    /** 턴 타이머 (초) — 초과 시 자동 드로우 + 최고 점수 카드 버림 */
    public static final int TURN_TIMER_SEC = 20;

    // ----------------------------------------------------------------
    // 플레이어 / 덱
    // ----------------------------------------------------------------

    /** 라운드 참가 최소 인원 */
    public static final int MIN_PLAYERS = 2;

    /** 라운드 참가 최대 인원 */
    public static final int MAX_PLAYERS = 5;

    /** 라운드 시작 시 플레이어별 초기 패 장수 */
    public static final int INITIAL_HAND_SIZE = 7;

    /** 파산 기준 핸드 장수 (이 이상이면 즉시 탈락) */
    public static final int BANKRUPTCY_HAND_SIZE = 10;

    /** 스탑 선언 가능한 핸드 점수 최대값 (이하이면 선언 가능) */
    public static final int STOP_SCORE_THRESHOLD = 10;

    /** 스톡 소진 후 버림더미로 재구성할 수 있는 최대 횟수 (초과 시 STOCK_DEPLETED 종료) */
    public static final int MAX_STOCK_REFILLS = 2;

    // ----------------------------------------------------------------
    // 점수
    // ----------------------------------------------------------------

    /** 7 카드를 핸드에 보유할 때 적용되는 점수 */
    public static final int SEVEN_SCORE = 14;

    /** 훌라(멜드 없이 고잉아웃) 시 승리 점수 배율 */
    public static final int HULA_MULTIPLIER = 4;

    /** 파산자 점수 계산 시 자신의 핸드 점수에 적용하는 배율 */
    public static final int BANKRUPTCY_SCORE_MULTIPLIER = 2;

    // ----------------------------------------------------------------
    // 멜드
    // ----------------------------------------------------------------

    /** 멜드당 허용되는 최대 거짓말 카드 장수 */
    public static final int MAX_BLUFFS_PER_MELD = 1;

    /** SET 멜드 최소 장수 */
    public static final int SET_MIN_SIZE = 3;

    /** SET 멜드 최대 장수 (확장 포함) */
    public static final int SET_MAX_SIZE = 4;

    /** STRAIGHT 멜드 최소 장수 */
    public static final int STRAIGHT_MIN_SIZE = 3;

    /** SOLO_SEVEN 멜드 카드 장수 (단독 7, 항상 1장) */
    public static final int SOLO_SEVEN_CARD_COUNT = 1;

    /** STRAIGHT에서 A를 K 다음으로 취급할 때의 값 (Q-K-A 조합) */
    public static final int ACE_HIGH_VALUE = 14;

    // ----------------------------------------------------------------
    // 연결
    // ----------------------------------------------------------------

    /** 이 횟수 이상 연결이 끊기면 자동 패배 처리 */
    public static final int MAX_DISCONNECT_COUNT = 3;
}
