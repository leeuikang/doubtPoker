package org.doubt.config;

import org.doubt.constant.ErrorCode;
import org.doubt.exception.GameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthStompErrorHandler")
class AuthStompErrorHandlerTest {

    private AuthStompErrorHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AuthStompErrorHandler();
    }

    // ----------------------------------------------------------------
    // 헬퍼
    // ----------------------------------------------------------------

    private String extractBody(Message<byte[]> message) {
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------------
    // handleClientMessageProcessingError
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("handleClientMessageProcessingError")
    class HandleClientMessageProcessingError {

        @Test
        @DisplayName("GameException(UNAUTHORIZED) — ERROR 프레임 바디가 'UNAUTHORIZED' 이다 (한국어 메시지 아님)")
        void gameException_unauthorized_returns_error_code_name() {
            GameException gameException = new GameException(ErrorCode.UNAUTHORIZED);

            Message<byte[]> result = handler.handleClientMessageProcessingError(null, gameException);

            assertThat(extractBody(result)).isEqualTo("UNAUTHORIZED");
            assertThat(extractBody(result)).isNotEqualTo(ErrorCode.UNAUTHORIZED.getMessage());
        }

        @Test
        @DisplayName("GameException(DUPLICATE_NICKNAME) — ERROR 프레임 바디가 'DUPLICATE_NICKNAME' 이다")
        void gameException_duplicateNickname_returns_error_code_name() {
            GameException gameException = new GameException(ErrorCode.DUPLICATE_NICKNAME);

            Message<byte[]> result = handler.handleClientMessageProcessingError(null, gameException);

            assertThat(extractBody(result)).isEqualTo("DUPLICATE_NICKNAME");
        }

        @Test
        @DisplayName("알 수 없는 RuntimeException — ERROR 프레임 바디가 'INTERNAL_SERVER_ERROR' 이다")
        void unknownException_returns_internal_server_error() {
            RuntimeException unknownException = new RuntimeException("예상치 못한 서버 오류");

            Message<byte[]> result = handler.handleClientMessageProcessingError(null, unknownException);

            assertThat(extractBody(result)).isEqualTo("INTERNAL_SERVER_ERROR");
        }

        @Test
        @DisplayName("cause 가 GameException 인 경우 — cause 의 ErrorCode 이름을 반환한다")
        void wrappedException_with_gameException_cause_returns_error_code_name() {
            GameException gameException = new GameException(ErrorCode.TOKEN_EXPIRED);
            RuntimeException wrapper = new RuntimeException("wrapper", gameException);

            Message<byte[]> result = handler.handleClientMessageProcessingError(null, wrapper);

            assertThat(extractBody(result)).isEqualTo("TOKEN_EXPIRED");
        }

        @Test
        @DisplayName("clientMessage 가 null 이어도 정상 처리한다")
        void null_clientMessage_does_not_throw() {
            GameException gameException = new GameException(ErrorCode.ROOM_NOT_FOUND);

            Message<byte[]> result = handler.handleClientMessageProcessingError(null, gameException);

            assertThat(result).isNotNull();
            assertThat(extractBody(result)).isEqualTo("ROOM_NOT_FOUND");
        }
    }
}
