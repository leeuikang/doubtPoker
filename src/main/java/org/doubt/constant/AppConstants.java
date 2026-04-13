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

    // 허용 출처 목록은 WebSocketConfig 에서 app.cors.extra-origins 프로퍼티로 동적 구성 (R2-I2)

    // ----------------------------------------------------------------
    // CSRF
    // ----------------------------------------------------------------

    /**
     * WebSocket 핸드셰이크 시 CSRF 토큰을 전달하는 쿼리 파라미터 이름.
     * 클라이언트: new SockJS('/websocket?' + CSRF_PARAM + '=' + csrfToken)
     */
    public static final String CSRF_PARAM = "csrf";
}
