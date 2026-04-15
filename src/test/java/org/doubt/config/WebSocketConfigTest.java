package org.doubt.config;

import org.doubt.constant.AppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2-I2: WebSocketConfig.buildAllowedOrigins() 단위 테스트
 *
 * app.cors.extra-origins 값에 따라 허용 출처 배열이 올바르게 구성되는지 검증한다.
 * Spring 컨텍스트 없이 ReflectionTestUtils를 사용하여 private 필드를 주입한다.
 */
@DisplayName("WebSocketConfig — buildAllowedOrigins (R2-I2)")
class WebSocketConfigTest {

    /**
     * 의존성 없이 WebSocketConfig 인스턴스를 생성하기 위한 최소 stub.
     * 실제 interceptor bean 주입 없이 buildAllowedOrigins()만 테스트한다.
     */
    private WebSocketConfig configWithExtraOrigins(String extraOrigins) {
        WebSocketConfig config = new WebSocketConfig(
                createStubStompAuthInterceptor(),
                createStubStompLogInterceptor(),
                createStubStompOutboundLogInterceptor(),
                createStubChatRateLimitInterceptor(),
                createStubGameRateLimitInterceptor(),
                createStubCsrfHandshakeInterceptor()
        );
        ReflectionTestUtils.setField(config, "corsExtraOrigins", extraOrigins);
        return config;
    }

    private org.doubt.interceptor.StompAuthInterceptor createStubStompAuthInterceptor() {
        return org.mockito.Mockito.mock(org.doubt.interceptor.StompAuthInterceptor.class);
    }

    private org.doubt.interceptor.StompLogInterceptor createStubStompLogInterceptor() {
        return org.mockito.Mockito.mock(org.doubt.interceptor.StompLogInterceptor.class);
    }

    private org.doubt.interceptor.StompOutboundLogInterceptor createStubStompOutboundLogInterceptor() {
        return org.mockito.Mockito.mock(org.doubt.interceptor.StompOutboundLogInterceptor.class);
    }

    private org.doubt.interceptor.ChatRateLimitInterceptor createStubChatRateLimitInterceptor() {
        return org.mockito.Mockito.mock(org.doubt.interceptor.ChatRateLimitInterceptor.class);
    }

    private org.doubt.interceptor.GameRateLimitInterceptor createStubGameRateLimitInterceptor() {
        return org.mockito.Mockito.mock(org.doubt.interceptor.GameRateLimitInterceptor.class);
    }

    private org.doubt.interceptor.CsrfHandshakeInterceptor createStubCsrfHandshakeInterceptor() {
        return org.mockito.Mockito.mock(org.doubt.interceptor.CsrfHandshakeInterceptor.class);
    }

    @Nested
    @DisplayName("buildAllowedOrigins")
    class BuildAllowedOrigins {

        @Test
        @DisplayName("corsExtraOrigins가 빈 문자열이면 PROD_ORIGIN만 포함된 배열을 반환한다")
        void empty_extra_origins_returns_prod_only() {
            WebSocketConfig config = configWithExtraOrigins("");

            String[] result = (String[]) ReflectionTestUtils.invokeMethod(config, "buildAllowedOrigins");

            assertThat(result).isNotNull();
            assertThat(result).containsExactly(AppConstants.PROD_ORIGIN);
        }

        @Test
        @DisplayName("corsExtraOrigins가 null이면 PROD_ORIGIN만 포함된 배열을 반환한다")
        void null_extra_origins_returns_prod_only() {
            WebSocketConfig config = configWithExtraOrigins(null);

            String[] result = (String[]) ReflectionTestUtils.invokeMethod(config, "buildAllowedOrigins");

            assertThat(result).isNotNull();
            assertThat(result).containsExactly(AppConstants.PROD_ORIGIN);
        }

        @Test
        @DisplayName("corsExtraOrigins가 공백 문자열이면 PROD_ORIGIN만 포함된 배열을 반환한다")
        void blank_extra_origins_returns_prod_only() {
            WebSocketConfig config = configWithExtraOrigins("   ");

            String[] result = (String[]) ReflectionTestUtils.invokeMethod(config, "buildAllowedOrigins");

            assertThat(result).isNotNull();
            assertThat(result).containsExactly(AppConstants.PROD_ORIGIN);
        }

        @Test
        @DisplayName("corsExtraOrigins가 설정되면 PROD_ORIGIN과 extra origin이 모두 포함된 배열을 반환한다")
        void non_blank_extra_origins_returns_prod_and_extra() {
            String devOrigin = AppConstants.DEV_ORIGIN;
            WebSocketConfig config = configWithExtraOrigins(devOrigin);

            String[] result = (String[]) ReflectionTestUtils.invokeMethod(config, "buildAllowedOrigins");

            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result).contains(AppConstants.PROD_ORIGIN, devOrigin);
        }

        @Test
        @DisplayName("임의의 추가 출처를 설정해도 PROD_ORIGIN이 배열의 첫 번째 원소로 유지된다")
        void prod_origin_is_always_first_element() {
            String extraOrigin = "https://staging.doubtpoker.io";
            WebSocketConfig config = configWithExtraOrigins(extraOrigin);

            String[] result = (String[]) ReflectionTestUtils.invokeMethod(config, "buildAllowedOrigins");

            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(AppConstants.PROD_ORIGIN);
            assertThat(result[1]).isEqualTo(extraOrigin);
        }
    }
}
