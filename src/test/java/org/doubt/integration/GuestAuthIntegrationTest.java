package org.doubt.integration;

import org.doubt.auth.GuestTokenService;
import org.doubt.doubtpoker.DoubtPokerApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POST /auth/guest REST 통합 테스트
 * Spring Context 전체 로딩, java.net.http.HttpClient 사용
 */
@SpringBootTest(classes = DoubtPokerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("POST /auth/guest 통합 테스트")
class GuestAuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private GuestTokenService guestTokenService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpResponse<String> postGuest(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/guest"))
                .header("Content-Type", "application/json")
                .POST(body != null
                        ? HttpRequest.BodyPublishers.ofString(body)
                        : HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String extractField(String json, String field) {
        // "field":"value" または "field":value
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int start = idx + key.length();
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        }
        int end = json.indexOf(',', start);
        if (end < 0) end = json.indexOf('}', start);
        String value = json.substring(start, end).trim();
        return value.equals("null") ? null : value;
    }

    @Nested
    @DisplayName("정상 케이스")
    class Success {

        @Test
        @DisplayName("유효한 닉네임으로 요청하면 200과 토큰을 반환한다")
        void valid_nickname_returns_token() throws Exception {
            HttpResponse<String> response = postGuest("{\"nickname\":\"홍길동\"}");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(extractField(response.body(), "token")).isNotNull().isNotEmpty();
            assertThat(extractField(response.body(), "guestId")).isNotNull().isNotEmpty();
            assertThat(extractField(response.body(), "nickname")).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("발급된 토큰은 GuestTokenService로 즉시 검증 가능하다")
        void issued_token_is_verifiable() throws Exception {
            HttpResponse<String> response = postGuest("{\"nickname\":\"검증테스트\"}");

            assertThat(response.statusCode()).isEqualTo(200);
            String token = extractField(response.body(), "token");

            assertThat(guestTokenService.verify(token).nickname()).isEqualTo("검증테스트");
        }

        @Test
        @DisplayName("닉네임 최대 길이(20자)로 요청하면 성공한다")
        void max_length_nickname_succeeds() throws Exception {
            String twentyChars = "a".repeat(20);
            HttpResponse<String> response = postGuest("{\"nickname\":\"" + twentyChars + "\"}");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(extractField(response.body(), "nickname")).isEqualTo(twentyChars);
        }

        @Test
        @DisplayName("동일 닉네임으로 두 번 발급해도 서로 다른 토큰이 반환된다")
        void same_nickname_produces_different_tokens() throws Exception {
            HttpResponse<String> r1 = postGuest("{\"nickname\":\"alice\"}");
            HttpResponse<String> r2 = postGuest("{\"nickname\":\"alice\"}");

            String token1 = extractField(r1.body(), "token");
            String token2 = extractField(r2.body(), "token");

            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("검증 실패 케이스")
    class ValidationFailure {

        @Test
        @DisplayName("닉네임이 빈 문자열이면 400을 반환한다")
        void blank_nickname_returns_400() throws Exception {
            assertThat(postGuest("{\"nickname\":\"\"}").statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("닉네임이 null이면 400을 반환한다")
        void null_nickname_returns_400() throws Exception {
            assertThat(postGuest("{\"nickname\":null}").statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("닉네임이 21자 이상이면 400을 반환한다")
        void too_long_nickname_returns_400() throws Exception {
            String twentyOneChars = "a".repeat(21);
            assertThat(postGuest("{\"nickname\":\"" + twentyOneChars + "\"}").statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("요청 바디가 없으면 400을 반환한다")
        void missing_body_returns_400() throws Exception {
            assertThat(postGuest(null).statusCode()).isEqualTo(400);
        }
    }
}
