package org.doubt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.doubt.auth.CsrfTokenResponse;
import org.doubt.auth.GuestClaims;
import org.doubt.auth.GuestTokenRequest;
import org.doubt.auth.GuestTokenResponse;
import org.doubt.auth.GuestTokenService;
import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * R2-I3: GuestAuthController 단위 테스트
 *
 * POST /auth/guest  — 인증 토큰 발급 (CSRF 토큰 미포함)
 * GET  /auth/csrf   — CSRF 토큰 별도 발급
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuestAuthController (R2-I3)")
class GuestAuthControllerTest {

    @Mock
    private GuestTokenService guestTokenService;

    @InjectMocks
    private GuestAuthController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ----------------------------------------------------------------
    // POST /auth/guest
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("POST /auth/guest — 인증 토큰 발급")
    class IssueGuestToken {

        @Test
        @DisplayName("유효한 닉네임으로 요청하면 token, guestId, nickname을 포함한 200 응답을 반환한다")
        void valid_request_returns_token_guestId_nickname() throws Exception {
            String token = "issued-token-value";
            GuestClaims claims = new GuestClaims("uuid-1234", "홍길동");
            when(guestTokenService.issue("홍길동")).thenReturn(token);
            when(guestTokenService.verify(token)).thenReturn(claims);

            mockMvc.perform(post("/auth/guest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"홍길동\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("issued-token-value"))
                    .andExpect(jsonPath("$.guestId").value("uuid-1234"))
                    .andExpect(jsonPath("$.nickname").value("홍길동"));
        }

        @Test
        @DisplayName("응답 JSON에 csrfToken 필드가 존재하지 않는다 (채널 분리 R2-I3)")
        void response_does_not_contain_csrf_token_field() throws Exception {
            String token = "some-token";
            GuestClaims claims = new GuestClaims("gid-abc", "테스터");
            when(guestTokenService.issue("테스터")).thenReturn(token);
            when(guestTokenService.verify(token)).thenReturn(claims);

            mockMvc.perform(post("/auth/guest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"테스터\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.csrfToken").doesNotExist());
        }

        @Test
        @DisplayName("GuestTokenResponse 레코드에 csrfToken 필드가 없다 (컴파일 레벨 구조 검증)")
        void guest_token_response_record_has_no_csrf_token_field() {
            // GuestTokenResponse(token, guestId, nickname) — 세 필드만 존재해야 한다
            GuestTokenResponse response = new GuestTokenResponse("t", "g", "n");
            assertThat(response.token()).isEqualTo("t");
            assertThat(response.guestId()).isEqualTo("g");
            assertThat(response.nickname()).isEqualTo("n");

            // csrfToken 컴포넌트가 없음을 리플렉션으로 확인
            boolean hasCsrfField = java.util.Arrays.stream(GuestTokenResponse.class.getRecordComponents())
                    .anyMatch(c -> c.getName().equals("csrfToken"));
            assertThat(hasCsrfField).isFalse();
        }

        @Test
        @DisplayName("issue()와 verify()가 각각 정확히 한 번 호출된다")
        void service_methods_called_once_each() throws Exception {
            String token = "tok";
            GuestClaims claims = new GuestClaims("gid", "닉네임");
            when(guestTokenService.issue("닉네임")).thenReturn(token);
            when(guestTokenService.verify(token)).thenReturn(claims);

            mockMvc.perform(post("/auth/guest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"닉네임\"}"))
                    .andExpect(status().isOk());

            verify(guestTokenService, times(1)).issue("닉네임");
            verify(guestTokenService, times(1)).verify(token);
        }
    }

    // ----------------------------------------------------------------
    // GET /auth/csrf
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("GET /auth/csrf — CSRF 토큰 발급")
    class IssueCsrfToken {

        @Test
        @DisplayName("유효한 guestId를 전달하면 csrfToken을 포함한 200 응답을 반환한다")
        void valid_guest_id_returns_csrf_token() throws Exception {
            String csrfToken = "csrf-token-value";
            when(guestTokenService.issueCsrfToken("valid-guest-id")).thenReturn(csrfToken);

            mockMvc.perform(get("/auth/csrf")
                            .param("guestId", "valid-guest-id"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.csrfToken").value("csrf-token-value"));
        }

        @Test
        @DisplayName("guestId가 빈 문자열이면 GameException(UNAUTHORIZED)이 발생한다")
        void blank_guest_id_throws_unauthorized() {
            assertThatThrownBy(() -> controller.issueCsrfToken(""))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(guestTokenService, never()).issueCsrfToken(anyString());
        }

        @Test
        @DisplayName("guestId가 공백 문자열이면 GameException(UNAUTHORIZED)이 발생한다")
        void whitespace_guest_id_throws_unauthorized() {
            assertThatThrownBy(() -> controller.issueCsrfToken("   "))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex -> assertThat(((GameException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(guestTokenService, never()).issueCsrfToken(anyString());
        }

        @Test
        @DisplayName("guestId 파라미터가 없으면 400(Bad Request)을 반환한다")
        void missing_guest_id_param_returns_400() throws Exception {
            mockMvc.perform(get("/auth/csrf"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("issueCsrfToken()이 정확히 한 번 호출된다")
        void service_method_called_once() throws Exception {
            when(guestTokenService.issueCsrfToken("gid-xyz")).thenReturn("csrf-tok");

            mockMvc.perform(get("/auth/csrf")
                            .param("guestId", "gid-xyz"))
                    .andExpect(status().isOk());

            verify(guestTokenService, times(1)).issueCsrfToken("gid-xyz");
        }

        @Test
        @DisplayName("CsrfTokenResponse 레코드는 csrfToken 필드 하나만 갖는다")
        void csrf_token_response_has_single_csrf_token_field() {
            CsrfTokenResponse response = new CsrfTokenResponse("my-csrf");
            assertThat(response.csrfToken()).isEqualTo("my-csrf");
            assertThat(CsrfTokenResponse.class.getRecordComponents()).hasSize(1);
        }
    }
}
