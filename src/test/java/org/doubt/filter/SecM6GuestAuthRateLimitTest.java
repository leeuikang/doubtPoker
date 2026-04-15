package org.doubt.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * SEC-M6 보안 취약점 수정 검증 테스트
 *
 * <p>원래 취약점(SEC-M6): {@code POST /auth/guest}에 레이트 리밋이 없어
 * 무제한 토큰 발급, 닉네임 고갈 공격, HMAC CPU 부하가 가능했다.</p>
 *
 * <p>수정 후: IP당 분당 최대 {@value GuestAuthRateLimitFilter#MAX_REQUESTS_PER_WINDOW}회로 제한.
 * 초과 시 HTTP 429를 반환한다.</p>
 */
@DisplayName("SEC-M6 수정 검증: POST /auth/guest IP 기반 레이트 리밋")
class SecM6GuestAuthRateLimitTest {

    private GuestAuthRateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new GuestAuthRateLimitFilter(new ObjectMapper());
        chain = mock(FilterChain.class);
    }

    // ----------------------------------------------------------------
    // 허용 범위 내 요청
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("허용 범위 내 요청")
    class WithinLimit {

        @Test
        @DisplayName("한도 이내 요청은 필터를 통과하여 체인이 호출된다")
        void within_limit_passes_filter() throws Exception {
            int limit = GuestAuthRateLimitFilter.MAX_REQUESTS_PER_WINDOW;

            for (int i = 0; i < limit; i++) {
                MockHttpServletRequest req = postGuestRequest("1.2.3.4");
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(req, res, chain);
                assertThat(res.getStatus()).isNotEqualTo(429);
            }

            verify(chain, times(limit)).doFilter(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
        }
    }

    // ----------------------------------------------------------------
    // 한도 초과 요청
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("한도 초과 요청")
    class ExceedLimit {

        @Test
        @DisplayName("한도 초과 시 HTTP 429를 반환한다")
        void over_limit_returns_429() throws Exception {
            int limit = GuestAuthRateLimitFilter.MAX_REQUESTS_PER_WINDOW;

            for (int i = 0; i < limit; i++) {
                MockHttpServletRequest req = postGuestRequest("2.3.4.5");
                filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
            }

            // 한도 초과 요청
            MockHttpServletRequest req = postGuestRequest("2.3.4.5");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(429);
        }

        @Test
        @DisplayName("한도 초과 응답의 Content-Type은 application/json이다")
        void over_limit_response_is_json() throws Exception {
            int limit = GuestAuthRateLimitFilter.MAX_REQUESTS_PER_WINDOW;

            for (int i = 0; i <= limit; i++) {
                MockHttpServletRequest req = postGuestRequest("3.4.5.6");
                filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
            }

            MockHttpServletRequest req = postGuestRequest("3.4.5.6");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, chain);

            assertThat(res.getContentType()).contains("application/json");
        }

        @Test
        @DisplayName("한도 초과 시 필터 체인은 호출되지 않는다")
        void over_limit_chain_not_called() throws Exception {
            int limit = GuestAuthRateLimitFilter.MAX_REQUESTS_PER_WINDOW;

            for (int i = 0; i < limit; i++) {
                filter.doFilterInternal(postGuestRequest("4.5.6.7"),
                        new MockHttpServletResponse(), chain);
            }

            // 초과 요청
            filter.doFilterInternal(postGuestRequest("4.5.6.7"),
                    new MockHttpServletResponse(), chain);

            // 체인은 limit번만 호출되어야 함
            verify(chain, times(limit)).doFilter(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
        }
    }

    // ----------------------------------------------------------------
    // IP 독립성
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("IP 독립성")
    class IpIndependence {

        @Test
        @DisplayName("서로 다른 IP는 별도 버킷을 사용한다")
        void different_ips_have_separate_buckets() throws Exception {
            int limit = GuestAuthRateLimitFilter.MAX_REQUESTS_PER_WINDOW;

            // IP A를 한도까지 소진
            for (int i = 0; i < limit; i++) {
                filter.doFilterInternal(postGuestRequest("10.0.0.1"),
                        new MockHttpServletResponse(), chain);
            }

            // IP B는 아직 여유 있음
            MockHttpServletRequest req = postGuestRequest("10.0.0.2");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, chain);

            assertThat(res.getStatus()).isNotEqualTo(429);
        }
    }

    // ----------------------------------------------------------------
    // 대상 경로 한정
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("대상 경로 한정")
    class PathFiltering {

        @Test
        @DisplayName("/auth/csrf 같은 다른 경로는 레이트 리밋 대상이 아니다")
        void non_target_path_is_not_rate_limited() throws Exception {
            int overLimit = GuestAuthRateLimitFilter.MAX_REQUESTS_PER_WINDOW + 5;

            for (int i = 0; i < overLimit; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest(HttpMethod.GET.name(), "/auth/csrf");
                req.setServletPath("/auth/csrf");
                req.setRemoteAddr("5.6.7.8");

                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(req, res, chain);

                assertThat(res.getStatus()).isNotEqualTo(429);
            }
        }

        @Test
        @DisplayName("POST가 아닌 메서드는 레이트 리밋 대상이 아니다")
        void non_post_method_is_not_rate_limited() throws Exception {
            int overLimit = GuestAuthRateLimitFilter.MAX_REQUESTS_PER_WINDOW + 5;

            for (int i = 0; i < overLimit; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest(HttpMethod.GET.name(), "/auth/guest");
                req.setServletPath("/auth/guest");
                req.setRemoteAddr("6.7.8.9");

                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(req, res, chain);

                assertThat(res.getStatus()).isNotEqualTo(429);
            }
        }
    }

    // ----------------------------------------------------------------
    // X-Forwarded-For 헤더
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("X-Forwarded-For 헤더 처리")
    class XForwardedFor {

        @Test
        @DisplayName("X-Forwarded-For 헤더가 있으면 첫 번째 IP를 사용한다")
        void uses_first_forwarded_ip() throws Exception {
            int limit = GuestAuthRateLimitFilter.MAX_REQUESTS_PER_WINDOW;

            for (int i = 0; i < limit; i++) {
                MockHttpServletRequest req = postGuestRequest("127.0.0.1");
                req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
                filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
            }

            // 같은 X-Forwarded-For IP로 초과 요청
            MockHttpServletRequest req = postGuestRequest("127.0.0.1");
            req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.2");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, chain);

            assertThat(res.getStatus()).isEqualTo(429);
        }
    }

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private MockHttpServletRequest postGuestRequest(String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest(HttpMethod.POST.name(), "/auth/guest");
        req.setServletPath("/auth/guest");
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}
