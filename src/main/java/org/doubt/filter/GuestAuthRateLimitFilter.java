package org.doubt.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doubt.constant.ErrorCode;
import org.doubt.dto.ErrorMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * POST /auth/guest IP 기반 레이트 리밋 필터 (SEC-M6)
 *
 * <p>닉네임 고갈 공격·HMAC CPU 부하·메모리 압박을 막기 위해
 * IP당 분당 최대 {@value #MAX_REQUESTS_PER_WINDOW}회로 제한한다.</p>
 *
 * <p>운영 환경에서는 Nginx/API Gateway에서 추가로 처리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestAuthRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_REQUESTS_PER_WINDOW = 10;
    private static final long WINDOW_MS = 60_000L;
    private static final String TARGET_PATH = "/auth/guest";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (!HttpMethod.POST.matches(request.getMethod())
                || !TARGET_PATH.equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        RateLimitBucket bucket = buckets.computeIfAbsent(ip, k -> new RateLimitBucket());

        if (!bucket.tryAcquire()) {
            log.warn("GuestAuthRateLimit: ip={} exceeded {} requests/min", ip, MAX_REQUESTS_PER_WINDOW);
            rejectWithTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** X-Forwarded-For 헤더를 우선 사용하고, 없으면 RemoteAddr를 사용한다. */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void rejectWithTooManyRequests(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.TOO_MANY_REQUESTS;
        ErrorMessage body = ErrorMessage.of(code.name(), code.getMessage());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static class RateLimitBucket {
        private long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= WINDOW_MS) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= MAX_REQUESTS_PER_WINDOW;
        }
    }
}
