package org.doubt.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CQ-W1, CQ-W2 코드 품질 수정 검증 테스트
 *
 * <p>CQ-W1: {@code ChatMessage.java} — {@code java.awt.TrayIcon.MessageType} import 제거.
 * {@code type} 필드를 내부 enum {@code ChatMessage.MessageType}으로 교체.</p>
 *
 * <p>CQ-W2: {@code PokerPlayer.java} — 필드 package-private 제거 {@code → private},
 * {@code @AllArgsConstructor}로 올바른 생성자 제공.</p>
 */
@DisplayName("CQ-W1, CQ-W2 수정 검증")
class CqW1W2FixTest {

    // ----------------------------------------------------------------
    // CQ-W1: ChatMessage
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("CQ-W1: ChatMessage — AWT import 제거 및 내부 enum 사용")
    class CqW1ChatMessage {

        @Test
        @DisplayName("type 필드의 타입은 ChatMessage.MessageType이다")
        void type_field_is_inner_enum() throws NoSuchFieldException {
            Field typeField = ChatMessage.class.getDeclaredField("type");
            assertThat(typeField.getType())
                    .as("type 필드는 ChatMessage.MessageType 이어야 한다")
                    .isEqualTo(ChatMessage.MessageType.class);
        }

        @Test
        @DisplayName("MessageType enum은 ENTER, TALK, LEAVE 값을 가진다")
        void message_type_enum_has_expected_values() {
            ChatMessage.MessageType[] values = ChatMessage.MessageType.values();
            assertThat(values)
                    .extracting(Enum::name)
                    .containsExactlyInAnyOrder("ENTER", "TALK", "LEAVE");
        }

        @Test
        @DisplayName("ChatMessage에 java.awt 관련 필드가 없다")
        void no_awt_fields() {
            for (Field field : ChatMessage.class.getDeclaredFields()) {
                assertThat(field.getType().getPackageName())
                        .as("필드 %s 의 타입이 java.awt 패키지가 아니어야 한다", field.getName())
                        .doesNotStartWith("java.awt");
            }
        }

        @Test
        @DisplayName("type 필드에 MessageType.TALK을 설정하고 getter로 읽을 수 있다")
        void set_and_get_message_type() {
            ChatMessage msg = new ChatMessage();
            msg.setType(ChatMessage.MessageType.TALK);
            assertThat(msg.getType()).isEqualTo(ChatMessage.MessageType.TALK);
        }
    }

    // ----------------------------------------------------------------
    // CQ-W2: PokerPlayer
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("CQ-W2: PokerPlayer — private 필드 및 생성자")
    class CqW2PokerPlayer {

        @Test
        @DisplayName("모든 필드가 private이다")
        void all_fields_are_private() {
            for (Field field : PokerPlayer.class.getDeclaredFields()) {
                // Lombok 합성 필드($jacocoData 등) 제외
                if (field.isSynthetic()) continue;
                assertThat(Modifier.isPrivate(field.getModifiers()))
                        .as("필드 '%s' 는 private 이어야 한다", field.getName())
                        .isTrue();
            }
        }

        @Test
        @DisplayName("빌더로 PokerPlayer를 생성하면 getter가 올바른 값을 반환한다")
        void builder_sets_fields_correctly() {
            PokerPlayer player = PokerPlayer.builder()
                    .sessionId("session-1")
                    .name("Alice")
                    .chips(100)
                    .isReady(true)
                    .build();

            assertThat(player.getSessionId()).isEqualTo("session-1");
            assertThat(player.getName()).isEqualTo("Alice");
            assertThat(player.getChips()).isEqualTo(100);
            assertThat(player.isReady()).isTrue();
        }

        @Test
        @DisplayName("기본 생성자로 PokerPlayer를 생성하면 기본값이 설정된다")
        void no_args_constructor_creates_default_player() {
            PokerPlayer player = new PokerPlayer();

            assertThat(player.getSessionId()).isNull();
            assertThat(player.getName()).isNull();
            assertThat(player.getChips()).isZero();
            assertThat(player.isReady()).isFalse();
        }

        @Test
        @DisplayName("name만 지정한 PokerPlayer에서 getName은 해당 이름을 반환한다")
        void player_with_name_returns_name() {
            PokerPlayer player = PokerPlayer.builder().name("Bob").build();
            assertThat(player.getName()).isEqualTo("Bob");
        }
    }
}
