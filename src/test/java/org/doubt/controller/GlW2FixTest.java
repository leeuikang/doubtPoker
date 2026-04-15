package org.doubt.controller;

import org.doubt.constant.ErrorCode;
import org.doubt.dto.ChatMessage;
import org.doubt.exception.GameException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * GL-W2 보안 수정 검증 테스트
 *
 * <p>수정 전: 클라이언트가 전달한 {@code ChatMessage.sender}를 그대로 사용해 닉네임 사칭 가능
 * 수정 후: 세션 속성의 {@code nickname}으로 {@code sender}를 강제 설정해 사칭 방지</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GL-W2: sendMessage — 닉네임 사칭 방지 수정 검증")
class GlW2FixTest {

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    private final ChatController chatController = new ChatController();

    // ================================================================
    // 테스트 1: 세션 닉네임으로 sender 강제 덮어쓰기 (핵심 보안 테스트)
    // ================================================================

    @Nested
    @DisplayName("sendMessage — sender 강제 덮어쓰기")
    class SendMessage_OverrideSender {

        @Test
        @DisplayName("[핵심 보안] 세션 nickname이 클라이언트 제공 sender를 덮어쓴다")
        void sendMessage_overridesSenderWithSessionNickname() {
            // given
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", "alice");
            when(headerAccessor.getSessionAttributes()).thenReturn(attrs);

            ChatMessage message = new ChatMessage();
            message.setSender("hacker");

            // when
            ChatMessage result = chatController.sendMessage(message, headerAccessor);

            // then: 클라이언트가 보낸 "hacker"가 아닌 세션의 "alice"가 적용돼야 한다
            assertThat(result.getSender()).isEqualTo("alice");
            assertThat(result.getSender()).isNotEqualTo("hacker");
        }
    }

    // ================================================================
    // 테스트 2: sender 외 필드는 변경되지 않음
    // ================================================================

    @Nested
    @DisplayName("sendMessage — sender 외 필드 보존")
    class SendMessage_OtherFieldsUnchanged {

        @Test
        @DisplayName("roomId, message 필드는 수정 없이 그대로 반환된다")
        void sendMessage_returnsMessageWithCorrectFields() {
            // given
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("nickname", "bob");
            when(headerAccessor.getSessionAttributes()).thenReturn(attrs);

            ChatMessage message = new ChatMessage();
            message.setRoomId("room1");
            message.setMessage("hello");
            message.setSender("anyone");

            // when
            ChatMessage result = chatController.sendMessage(message, headerAccessor);

            // then: sender만 세션값으로 교체되고 나머지 필드는 유지된다
            assertThat(result.getSender()).isEqualTo("bob");
            assertThat(result.getRoomId()).isEqualTo("room1");
            assertThat(result.getMessage()).isEqualTo("hello");
        }
    }

    // ================================================================
    // 테스트 3: 세션에 nickname 없으면 UNAUTHORIZED 예외
    // ================================================================

    @Nested
    @DisplayName("sendMessage — nickname 누락 시 예외")
    class SendMessage_NicknameNull {

        @Test
        @DisplayName("세션에 nickname 키가 없으면 UNAUTHORIZED GameException을 던진다")
        void sendMessage_whenNicknameNull_throwsUnauthorized() {
            // given: attrs에 nickname 키가 없음
            Map<String, Object> attrs = new HashMap<>();
            when(headerAccessor.getSessionAttributes()).thenReturn(attrs);

            ChatMessage message = new ChatMessage();
            message.setSender("attacker");

            // when & then
            assertThatThrownBy(() -> chatController.sendMessage(message, headerAccessor))
                    .isInstanceOf(GameException.class)
                    .satisfies(ex ->
                            assertThat(((GameException) ex).getErrorCode())
                                    .isEqualTo(ErrorCode.UNAUTHORIZED)
                    );
        }
    }

    // ================================================================
    // 테스트 4: getSessionAttributes()가 null이면 NullPointerException
    // ================================================================

    @Nested
    @DisplayName("sendMessage — getSessionAttributes() null 시 안전장치")
    class SendMessage_SessionAttributesNull {

        @Test
        @DisplayName("getSessionAttributes()가 null이면 NullPointerException이 발생한다")
        void sendMessage_whenSessionAttributesNull_throwsNullPointerException() {
            // given: Objects.requireNonNull에 의해 NPE가 발생하는 상황
            when(headerAccessor.getSessionAttributes()).thenReturn(null);

            ChatMessage message = new ChatMessage();

            // when & then
            assertThatThrownBy(() -> chatController.sendMessage(message, headerAccessor))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
