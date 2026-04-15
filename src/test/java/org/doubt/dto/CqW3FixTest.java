package org.doubt.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CQ-W3 코드 품질 수정 검증 테스트
 *
 * <p>원래 문제(CQ-W3): {@code PokerRoom.java}에 사용되지 않는 레거시 필드
 * {@code currentIndex}, {@code totalPot}이 잔류해 혼란을 유발했다.</p>
 *
 * <p>수정 후: 두 필드 모두 삭제.</p>
 */
@DisplayName("CQ-W3 수정 검증: PokerRoom 미사용 필드 제거")
class CqW3FixTest {

    @Test
    @DisplayName("PokerRoom에 currentIndex 필드가 없다")
    void no_currentIndex_field() {
        Set<String> fieldNames = Arrays.stream(PokerRoom.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames)
                .as("currentIndex 필드는 PokerRoom에서 제거되어야 한다")
                .doesNotContain("currentIndex");
    }

    @Test
    @DisplayName("PokerRoom에 totalPot 필드가 없다")
    void no_totalPot_field() {
        Set<String> fieldNames = Arrays.stream(PokerRoom.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames)
                .as("totalPot 필드는 PokerRoom에서 제거되어야 한다")
                .doesNotContain("totalPot");
    }

    @Test
    @DisplayName("PokerRoom 생성자가 정상 동작한다")
    void constructor_still_works() {
        assertThatCode(() -> new PokerRoom("r1", "테스트방"))
                .doesNotThrowAnyException();
    }
}
