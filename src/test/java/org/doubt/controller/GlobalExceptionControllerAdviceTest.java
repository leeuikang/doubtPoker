package org.doubt.controller;

import org.doubt.constant.ErrorCode;
import org.doubt.dto.ErrorMessage;
import org.doubt.dto.GameMessage;
import org.doubt.exception.GameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionControllerAdvice")
class GlobalExceptionControllerAdviceTest {

    private GlobalExceptionControllerAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new GlobalExceptionControllerAdvice();
    }

    @Nested
    @DisplayName("handleGameException")
    class HandleGameException {

        @Test
        @DisplayName("반환 타입이 GameMessage이며 type=ERROR, roomId=null, sender=SERVER이다")
        void returns_game_message_with_correct_envelope_fields() {
            GameException exception = new GameException(ErrorCode.INVALID_TURN);

            GameMessage result = advice.handleGameException(exception);

            assertThat(result.type()).isEqualTo("ERROR");
            assertThat(result.roomId()).isNull();
            assertThat(result.sender()).isEqualTo("SERVER");
        }

        @Test
        @DisplayName("payload가 ErrorMessage 인스턴스이다")
        void payload_is_error_message_instance() {
            GameException exception = new GameException(ErrorCode.INVALID_TURN);

            GameMessage result = advice.handleGameException(exception);

            assertThat(result.payload()).isInstanceOf(ErrorMessage.class);
        }

        @Test
        @DisplayName("payload의 message 필드가 ErrorCode.getMessage() 값과 일치한다")
        void returns_errorCode_message_as_message_field() {
            GameException exception = new GameException(ErrorCode.INVALID_TURN);

            GameMessage result = advice.handleGameException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.message()).isEqualTo(ErrorCode.INVALID_TURN.getMessage());
        }

        @Test
        @DisplayName("payload의 errorCode 필드가 ErrorCode enum name 문자열과 일치한다")
        void returns_errorCode_name_as_errorCode_field() {
            GameException exception = new GameException(ErrorCode.ROOM_NOT_FOUND);

            GameMessage result = advice.handleGameException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.errorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND.name());
        }

        @Test
        @DisplayName("예외 클래스 이름이 클라이언트 응답에 노출되지 않는다")
        void does_not_expose_exception_class_name() {
            GameException exception = new GameException(ErrorCode.INVALID_MELD);

            GameMessage result = advice.handleGameException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.message()).doesNotContain("GameException");
            assertThat(payload.message()).doesNotContain("Exception");
            assertThat(payload.errorCode()).doesNotContain("Exception");
        }

        @Test
        @DisplayName("스택 트레이스가 클라이언트 응답에 노출되지 않는다")
        void does_not_expose_stack_trace() {
            GameException exception = new GameException(ErrorCode.INVALID_CARD);

            GameMessage result = advice.handleGameException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.message()).doesNotContain("at org.doubt");
            assertThat(payload.message()).doesNotContain("StackTrace");
        }

        @Test
        @DisplayName("raw 예외 메시지가 아닌 ErrorCode.getMessage()를 반환한다")
        void returns_errorCode_getMessage_not_raw_exception_message() {
            GameException exception = new GameException(ErrorCode.CANNOT_DOUBT);

            GameMessage result = advice.handleGameException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.message()).isEqualTo(ErrorCode.CANNOT_DOUBT.getMessage());
            assertThat(payload.message()).isNotNull();
        }

        @Test
        @DisplayName("traceId와 timestamp가 null이 아니다 (추적 가능성 보장)")
        void traceId_and_timestamp_are_not_null() {
            GameException exception = new GameException(ErrorCode.INVALID_TURN);

            GameMessage result = advice.handleGameException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.traceId()).isNotNull().isNotBlank();
            assertThat(payload.timestamp()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("여러 ErrorCode에 대해 각각의 errorCode name과 message를 올바르게 반환한다")
        void returns_correct_fields_for_each_error_code() {
            for (ErrorCode errorCode : ErrorCode.values()) {
                if (errorCode == ErrorCode.INTERNAL_SERVER_ERROR) {
                    continue; // handleException에서 처리되는 코드 제외
                }
                GameException exception = new GameException(errorCode);

                GameMessage result = advice.handleGameException(exception);
                ErrorMessage payload = (ErrorMessage) result.payload();

                assertThat(payload.errorCode())
                        .as("ErrorCode %s 의 name이 일치해야 한다", errorCode)
                        .isEqualTo(errorCode.name());
                assertThat(payload.message())
                        .as("ErrorCode %s 의 메시지가 일치해야 한다", errorCode)
                        .isEqualTo(errorCode.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("handleException")
    class HandleException {

        @Test
        @DisplayName("반환 타입이 GameMessage이며 type=ERROR, roomId=null, sender=SERVER이다")
        void returns_game_message_with_correct_envelope_fields() {
            Exception exception = new RuntimeException("DB connection failed");

            GameMessage result = advice.handleException(exception);

            assertThat(result.type()).isEqualTo("ERROR");
            assertThat(result.roomId()).isNull();
            assertThat(result.sender()).isEqualTo("SERVER");
        }

        @Test
        @DisplayName("payload가 ErrorMessage 인스턴스이다")
        void payload_is_error_message_instance() {
            Exception exception = new RuntimeException("DB connection failed");

            GameMessage result = advice.handleException(exception);

            assertThat(result.payload()).isInstanceOf(ErrorMessage.class);
        }

        @Test
        @DisplayName("payload의 message 필드가 '서버 에러입니다.'이다")
        void returns_generic_message() {
            Exception exception = new RuntimeException("DB connection failed");

            GameMessage result = advice.handleException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.message()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
            assertThat(payload.message()).isEqualTo("서버 에러입니다.");
        }

        @Test
        @DisplayName("payload의 errorCode 필드가 INTERNAL_SERVER_ERROR 문자열이다")
        void returns_internal_server_error_code() {
            Exception exception = new RuntimeException("NullPointerException in service");

            GameMessage result = advice.handleException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.errorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        }

        @Test
        @DisplayName("실제 예외 메시지가 클라이언트 응답에 노출되지 않는다")
        void does_not_expose_actual_exception_message() {
            String sensitiveMessage = "DB connection failed: host=192.168.1.1 password=secret";
            Exception exception = new RuntimeException(sensitiveMessage);

            GameMessage result = advice.handleException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.message()).doesNotContain(sensitiveMessage);
            assertThat(payload.message()).doesNotContain("192.168.1.1");
            assertThat(payload.message()).doesNotContain("secret");
        }

        @Test
        @DisplayName("예외 클래스 이름이 클라이언트 응답에 노출되지 않는다")
        void does_not_expose_exception_class_name() {
            Exception exception = new NullPointerException("null ref in RoundService");

            GameMessage result = advice.handleException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.message()).doesNotContain("NullPointerException");
            assertThat(payload.errorCode()).doesNotContain("NullPointerException");
        }

        @Test
        @DisplayName("traceId와 timestamp가 null이 아니다 (추적 가능성 보장)")
        void traceId_and_timestamp_are_not_null() {
            Exception exception = new IllegalStateException("unexpected state");

            GameMessage result = advice.handleException(exception);
            ErrorMessage payload = (ErrorMessage) result.payload();

            assertThat(payload.traceId()).isNotNull().isNotBlank();
            assertThat(payload.timestamp()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("호출할 때마다 고유한 traceId가 생성된다")
        void generates_unique_trace_id_each_time() {
            Exception exception = new RuntimeException("error");

            GameMessage first = advice.handleException(exception);
            GameMessage second = advice.handleException(exception);

            ErrorMessage firstPayload = (ErrorMessage) first.payload();
            ErrorMessage secondPayload = (ErrorMessage) second.payload();

            assertThat(firstPayload.traceId()).isNotEqualTo(secondPayload.traceId());
        }
    }
}
