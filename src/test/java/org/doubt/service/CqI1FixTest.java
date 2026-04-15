package org.doubt.service;

import org.doubt.constant.MeldType;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CQ-I1 수정 검증 테스트
 *
 * <p>원래 문제(CQ-I1): {@code MeldValidationService.isConsecutiveStraight}에서
 * {@code Collectors.toList()} 사용 및 {@code Rank.ACE.getBasePoint()} 중복 호출.</p>
 *
 * <p>수정 후: {@code .toList()} 사용, {@code int aceValue} 지역 변수로 중복 제거,
 * {@code import java.util.stream.Collectors} 제거.</p>
 *
 * <p>STRAIGHT 규칙: 같은 무늬의 연속된 숫자 3장 이상.</p>
 */
@DisplayName("CQ-I1 수정 검증: MeldValidationService isConsecutiveStraight 리팩터링")
class CqI1FixTest {

    private final MeldValidationService service = new MeldValidationService();

    // ----------------------------------------------------------------
    // isConsecutiveStraight 동작 검증 — A=1 (일반 순서)
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("STRAIGHT 검증 — A=1 (일반 순서, 같은 무늬)")
    class StraightAceLow {

        @Test
        @DisplayName("A-2-3 STRAIGHT는 유효하다")
        void ace_two_three_straight_is_valid() {
            List<Card> actual = List.of(
                    new Card(Suit.SPADE, Rank.ACE),
                    new Card(Suit.SPADE, Rank.TWO),
                    new Card(Suit.SPADE, Rank.THREE)
            );
            List<DeclaredCard> declared = List.of(
                    new DeclaredCard(Suit.SPADE, Rank.ACE),
                    new DeclaredCard(Suit.SPADE, Rank.TWO),
                    new DeclaredCard(Suit.SPADE, Rank.THREE)
            );
            assertThat(service.validateMeld(actual, declared, MeldType.STRAIGHT)).isTrue();
        }

        @Test
        @DisplayName("A-2-3-4 STRAIGHT는 유효하다")
        void ace_two_three_four_straight_is_valid() {
            List<Card> actual = List.of(
                    new Card(Suit.SPADE, Rank.ACE),
                    new Card(Suit.SPADE, Rank.TWO),
                    new Card(Suit.SPADE, Rank.THREE),
                    new Card(Suit.SPADE, Rank.FOUR)
            );
            List<DeclaredCard> declared = List.of(
                    new DeclaredCard(Suit.SPADE, Rank.ACE),
                    new DeclaredCard(Suit.SPADE, Rank.TWO),
                    new DeclaredCard(Suit.SPADE, Rank.THREE),
                    new DeclaredCard(Suit.SPADE, Rank.FOUR)
            );
            assertThat(service.validateMeld(actual, declared, MeldType.STRAIGHT)).isTrue();
        }
    }

    // ----------------------------------------------------------------
    // isConsecutiveStraight 동작 검증 — A=14 (하이 에이스)
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("STRAIGHT 검증 — A=14 (하이 에이스, 같은 무늬)")
    class StraightAceHigh {

        @Test
        @DisplayName("Q-K-A STRAIGHT는 유효하다 (aceValue 지역 변수 경로)")
        void queen_king_ace_straight_is_valid() {
            List<Card> actual = List.of(
                    new Card(Suit.SPADE, Rank.QUEEN),
                    new Card(Suit.SPADE, Rank.KING),
                    new Card(Suit.SPADE, Rank.ACE)
            );
            List<DeclaredCard> declared = List.of(
                    new DeclaredCard(Suit.SPADE, Rank.QUEEN),
                    new DeclaredCard(Suit.SPADE, Rank.KING),
                    new DeclaredCard(Suit.SPADE, Rank.ACE)
            );
            assertThat(service.validateMeld(actual, declared, MeldType.STRAIGHT)).isTrue();
        }

        @Test
        @DisplayName("J-Q-K-A STRAIGHT는 유효하다")
        void jack_queen_king_ace_straight_is_valid() {
            List<Card> actual = List.of(
                    new Card(Suit.SPADE, Rank.JACK),
                    new Card(Suit.SPADE, Rank.QUEEN),
                    new Card(Suit.SPADE, Rank.KING),
                    new Card(Suit.SPADE, Rank.ACE)
            );
            List<DeclaredCard> declared = List.of(
                    new DeclaredCard(Suit.SPADE, Rank.JACK),
                    new DeclaredCard(Suit.SPADE, Rank.QUEEN),
                    new DeclaredCard(Suit.SPADE, Rank.KING),
                    new DeclaredCard(Suit.SPADE, Rank.ACE)
            );
            assertThat(service.validateMeld(actual, declared, MeldType.STRAIGHT)).isTrue();
        }
    }

    // ----------------------------------------------------------------
    // 일반 중간 순서 STRAIGHT
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("일반 STRAIGHT 검증")
    class GeneralStraight {

        @Test
        @DisplayName("5-6-8 연속되지 않는 카드는 STRAIGHT가 아니다")
        void non_consecutive_is_not_straight() {
            List<Card> actual = List.of(
                    new Card(Suit.SPADE, Rank.FIVE),
                    new Card(Suit.SPADE, Rank.SIX),
                    new Card(Suit.SPADE, Rank.EIGHT)
            );
            List<DeclaredCard> declared = List.of(
                    new DeclaredCard(Suit.SPADE, Rank.FIVE),
                    new DeclaredCard(Suit.SPADE, Rank.SIX),
                    new DeclaredCard(Suit.SPADE, Rank.EIGHT)
            );
            assertThat(service.validateMeld(actual, declared, MeldType.STRAIGHT)).isFalse();
        }

        @Test
        @DisplayName("서로 다른 무늬는 STRAIGHT가 아니다")
        void different_suits_is_not_straight() {
            List<Card> actual = List.of(
                    new Card(Suit.SPADE, Rank.FIVE),
                    new Card(Suit.HEART, Rank.SIX),
                    new Card(Suit.SPADE, Rank.SEVEN)
            );
            List<DeclaredCard> declared = List.of(
                    new DeclaredCard(Suit.SPADE, Rank.FIVE),
                    new DeclaredCard(Suit.HEART, Rank.SIX),
                    new DeclaredCard(Suit.SPADE, Rank.SEVEN)
            );
            assertThat(service.validateMeld(actual, declared, MeldType.STRAIGHT)).isFalse();
        }
    }
}
