package org.doubt.exception;

import org.doubt.constant.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GameException")
class GameExceptionTest {

    @Nested
    @DisplayName("단일 인자 생성자 (ErrorCode)")
    class SingleArgConstructor {

        @Test
        @DisplayName("errorCode 필드가 전달된 ErrorCode와 동일하다")
        void stores_errorCode() {
            GameException exception = new GameException(ErrorCode.INVALID_TURN);

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TURN);
        }

        @Test
        @DisplayName("getMessage()는 errorCode.getMessage()와 동일한 값을 반환한다")
        void getMessage_returns_errorCode_getMessage() {
            GameException exception = new GameException(ErrorCode.ROOM_NOT_FOUND);

            assertThat(exception.getMessage()).isEqualTo(ErrorCode.ROOM_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("getCause()는 null이다 (cause 없이 생성)")
        void getCause_is_null() {
            GameException exception = new GameException(ErrorCode.INVALID_MELD);

            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("모든 ErrorCode에 대해 errorCode 필드와 getMessage()가 일관성을 가진다")
        void consistent_for_all_error_codes() {
            for (ErrorCode errorCode : ErrorCode.values()) {
                GameException exception = new GameException(errorCode);

                assertThat(exception.getErrorCode())
                        .as("ErrorCode %s 의 errorCode 필드가 일치해야 한다", errorCode)
                        .isEqualTo(errorCode);
                assertThat(exception.getMessage())
                        .as("ErrorCode %s 의 getMessage()가 일치해야 한다", errorCode)
                        .isEqualTo(errorCode.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("두 인자 생성자 (ErrorCode, Throwable)")
    class TwoArgConstructor {

        @Test
        @DisplayName("errorCode 필드가 전달된 ErrorCode와 동일하다")
        void stores_errorCode() {
            Throwable cause = new IllegalStateException("원인 예외");
            GameException exception = new GameException(ErrorCode.INVALID_CARD, cause);

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CARD);
        }

        @Test
        @DisplayName("getMessage()는 errorCode.getMessage()와 동일한 값을 반환한다")
        void getMessage_returns_errorCode_getMessage() {
            Throwable cause = new RuntimeException("DB 연결 실패");
            GameException exception = new GameException(ErrorCode.INTERNAL_SERVER_ERROR, cause);

            assertThat(exception.getMessage()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        }

        @Test
        @DisplayName("getCause()가 전달된 cause를 그대로 반환한다 (원인 체인 보존)")
        void getCause_returns_passed_cause() {
            Throwable cause = new IllegalArgumentException("잘못된 인자");
            GameException exception = new GameException(ErrorCode.INVALID_BLUFF, cause);

            assertThat(exception.getCause()).isSameAs(cause);
        }

        @Test
        @DisplayName("중첩된 cause 체인이 보존된다")
        void nested_cause_chain_is_preserved() {
            Throwable rootCause = new NullPointerException("null 참조");
            Throwable middleCause = new RuntimeException("중간 예외", rootCause);
            GameException exception = new GameException(ErrorCode.INVALID_TURN, middleCause);

            assertThat(exception.getCause()).isSameAs(middleCause);
            assertThat(exception.getCause().getCause()).isSameAs(rootCause);
        }

        @Test
        @DisplayName("cause로 null을 전달해도 예외 없이 생성된다")
        void null_cause_is_accepted() {
            GameException exception = new GameException(ErrorCode.CANNOT_STOP, null);

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CANNOT_STOP);
            assertThat(exception.getMessage()).isEqualTo(ErrorCode.CANNOT_STOP.getMessage());
            assertThat(exception.getCause()).isNull();
        }
    }
}
