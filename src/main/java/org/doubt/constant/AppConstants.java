package org.doubt.constant;

/** 애플리케이션 인프라 상수 (도메인·보안·서버 설정) */
public final class AppConstants {

    private AppConstants() {}

    // ----------------------------------------------------------------
    // CORS 허용 출처
    // ----------------------------------------------------------------

    /** 프로덕션 프론트엔드 도메인 */
    public static final String PROD_ORIGIN = "https://doubtpoker.io";

    /** 로컬 개발 서버 (Vite 기본 포트) */
    public static final String DEV_ORIGIN = "http://localhost:5173";

    /**
     * WebSocket CORS 허용 출처 목록.
     * 배포 도메인 변경 시 이 배열만 수정하면 전체 적용된다.
     */
    public static final String[] ALLOWED_ORIGINS = { PROD_ORIGIN, DEV_ORIGIN };
}
