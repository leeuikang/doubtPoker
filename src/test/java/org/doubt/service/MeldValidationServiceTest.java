package org.doubt.service;

import org.doubt.constant.MeldType;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;
import org.doubt.dto.Meld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MeldValidationServiceTest {

    private MeldValidationService service;

    @BeforeEach
    void setUp() {
        service = new MeldValidationService();
    }

    // --- 리플렉션 헬퍼 ---

    private Meld buildMeld(MeldType type, List<DeclaredCard> declared) throws Exception {
        Meld meld = new Meld();
        setField(meld, "type", type);
        setField(meld, "declaredCards", declared);
        return meld;
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj, value);
    }

    // --- 카드 생성 헬퍼 ---

    private Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    private DeclaredCard declared(Suit suit, Rank rank) {
        return new DeclaredCard(suit, rank);
    }

    // ==================== validateMeld ====================

    @Nested
    @DisplayName("validateMeld - SET")
    class ValidateMeldSet {

        @Test
        @DisplayName("같은 숫자 3장 SET은 유효하다")
        void three_same_rank_is_valid_set() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.KING),
                    card(Suit.HEART, Rank.KING),
                    card(Suit.DIAMOND, Rank.KING)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            );
            assertTrue(service.validateMeld(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("같은 숫자 4장 SET은 유효하다")
        void four_same_rank_is_valid_set() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.ACE),
                    card(Suit.HEART, Rank.ACE),
                    card(Suit.DIAMOND, Rank.ACE),
                    card(Suit.CLUB, Rank.ACE)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.ACE),
                    declared(Suit.HEART, Rank.ACE),
                    declared(Suit.DIAMOND, Rank.ACE),
                    declared(Suit.CLUB, Rank.ACE)
            );
            assertTrue(service.validateMeld(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("SET 2장은 무효이다")
        void two_cards_set_is_invalid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.FIVE),
                    card(Suit.HEART, Rank.FIVE)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.HEART, Rank.FIVE)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("SET 5장은 무효이다")
        void five_cards_set_is_invalid() {
            // 5장은 실제로 4개 수트밖에 없으므로 선언도 5장으로 구성
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.THREE),
                    card(Suit.HEART, Rank.THREE),
                    card(Suit.DIAMOND, Rank.THREE),
                    card(Suit.CLUB, Rank.THREE),
                    card(Suit.SPADE, Rank.THREE)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.THREE),
                    declared(Suit.HEART, Rank.THREE),
                    declared(Suit.DIAMOND, Rank.THREE),
                    declared(Suit.CLUB, Rank.THREE),
                    declared(Suit.SPADE, Rank.THREE)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("SET에서 숫자가 서로 다르면 무효이다")
        void different_ranks_set_is_invalid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.TWO),
                    card(Suit.HEART, Rank.THREE),
                    card(Suit.DIAMOND, Rank.TWO)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.TWO),
                    declared(Suit.HEART, Rank.THREE),
                    declared(Suit.DIAMOND, Rank.TWO)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("SET에서 실제 카드 1장이 다른 거짓말 멜드는 유효하다")
        void set_with_one_bluff_card_is_valid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.KING),
                    card(Suit.HEART, Rank.KING),
                    card(Suit.DIAMOND, Rank.QUEEN) // 실제는 QUEEN
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING) // 선언은 KING
            );
            assertTrue(service.validateMeld(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("SET에서 실제 카드 2장이 다른 거짓말 멜드는 무효이다")
        void set_with_two_bluff_cards_is_invalid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.KING),
                    card(Suit.HEART, Rank.QUEEN), // 실제는 QUEEN
                    card(Suit.DIAMOND, Rank.JACK)  // 실제는 JACK
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.SET));
        }
    }

    @Nested
    @DisplayName("validateMeld - STRAIGHT")
    class ValidateMeldStraight {

        @Test
        @DisplayName("같은 무늬 연속 3장 STRAIGHT는 유효하다")
        void three_consecutive_same_suit_is_valid_straight() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.FOUR),
                    card(Suit.SPADE, Rank.FIVE),
                    card(Suit.SPADE, Rank.SIX)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.FOUR),
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX)
            );
            assertTrue(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("같은 무늬 연속 5장 STRAIGHT는 유효하다")
        void five_consecutive_same_suit_is_valid_straight() {
            List<Card> actual = List.of(
                    card(Suit.HEART, Rank.THREE),
                    card(Suit.HEART, Rank.FOUR),
                    card(Suit.HEART, Rank.FIVE),
                    card(Suit.HEART, Rank.SIX),
                    card(Suit.HEART, Rank.SEVEN)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.HEART, Rank.THREE),
                    declared(Suit.HEART, Rank.FOUR),
                    declared(Suit.HEART, Rank.FIVE),
                    declared(Suit.HEART, Rank.SIX),
                    declared(Suit.HEART, Rank.SEVEN)
            );
            assertTrue(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("A-2-3 순환 STRAIGHT는 유효하다")
        void ace_low_wrap_straight_is_valid() {
            List<Card> actual = List.of(
                    card(Suit.CLUB, Rank.ACE),
                    card(Suit.CLUB, Rank.TWO),
                    card(Suit.CLUB, Rank.THREE)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.CLUB, Rank.ACE),
                    declared(Suit.CLUB, Rank.TWO),
                    declared(Suit.CLUB, Rank.THREE)
            );
            assertTrue(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("Q-K-A 순환 STRAIGHT는 유효하다")
        void ace_high_wrap_straight_is_valid() {
            List<Card> actual = List.of(
                    card(Suit.DIAMOND, Rank.QUEEN),
                    card(Suit.DIAMOND, Rank.KING),
                    card(Suit.DIAMOND, Rank.ACE)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.DIAMOND, Rank.QUEEN),
                    declared(Suit.DIAMOND, Rank.KING),
                    declared(Suit.DIAMOND, Rank.ACE)
            );
            assertTrue(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("K-A-2 (A가 중간) STRAIGHT는 무효이다")
        void king_ace_two_middle_ace_straight_is_invalid() {
            // K=13, A=1 or 14. 1,2,13 은 연속이 아니고 12,13,14도 연속이 아니다
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.KING),
                    card(Suit.SPADE, Rank.ACE),
                    card(Suit.SPADE, Rank.TWO)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.SPADE, Rank.ACE),
                    declared(Suit.SPADE, Rank.TWO)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("STRAIGHT 2장은 무효이다")
        void two_cards_straight_is_invalid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.FIVE),
                    card(Suit.SPADE, Rank.SIX)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("무늬가 섞인 STRAIGHT는 무효이다")
        void mixed_suit_straight_is_invalid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.FOUR),
                    card(Suit.HEART, Rank.FIVE),
                    card(Suit.SPADE, Rank.SIX)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.FOUR),
                    declared(Suit.HEART, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("연속되지 않은 STRAIGHT는 무효이다")
        void non_consecutive_straight_is_invalid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.THREE),
                    card(Suit.SPADE, Rank.FIVE),
                    card(Suit.SPADE, Rank.SEVEN)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.THREE),
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SEVEN)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("STRAIGHT에서 실제 카드 1장이 다른 거짓말 멜드는 유효하다")
        void straight_with_one_bluff_card_is_valid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.FOUR),
                    card(Suit.SPADE, Rank.FIVE),
                    card(Suit.HEART, Rank.TWO) // 실제는 HEART TWO
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.FOUR),
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX) // 선언은 SPADE SIX
            );
            assertTrue(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }

        @Test
        @DisplayName("STRAIGHT에서 실제 카드 2장이 다른 거짓말 멜드는 무효이다")
        void straight_with_two_bluff_cards_is_invalid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.FOUR),
                    card(Suit.HEART, Rank.TWO),  // 실제는 HEART TWO
                    card(Suit.CLUB, Rank.JACK)    // 실제는 CLUB JACK
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.FOUR),
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.STRAIGHT));
        }
    }

    @Nested
    @DisplayName("validateMeld - SOLO_SEVEN")
    class ValidateMeldSoloSeven {

        @Test
        @DisplayName("실제 7, 선언 7 SOLO_SEVEN은 유효하다")
        void actual_seven_declared_seven_is_valid() {
            List<Card> actual = List.of(card(Suit.SPADE, Rank.SEVEN));
            List<DeclaredCard> declared = List.of(declared(Suit.SPADE, Rank.SEVEN));
            assertTrue(service.validateMeld(actual, declared, MeldType.SOLO_SEVEN));
        }

        @Test
        @DisplayName("SOLO_SEVEN에서 거짓말이 있으면 무효이다")
        void solo_seven_with_bluff_is_invalid() {
            List<Card> actual = List.of(card(Suit.SPADE, Rank.EIGHT)); // 실제는 8
            List<DeclaredCard> declared = List.of(declared(Suit.SPADE, Rank.SEVEN)); // 선언은 7
            assertFalse(service.validateMeld(actual, declared, MeldType.SOLO_SEVEN));
        }

        @Test
        @DisplayName("SOLO_SEVEN에 2장 이상이면 무효이다")
        void solo_seven_with_multiple_cards_is_invalid() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.SEVEN),
                    card(Suit.HEART, Rank.SEVEN)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.SEVEN),
                    declared(Suit.HEART, Rank.SEVEN)
            );
            assertFalse(service.validateMeld(actual, declared, MeldType.SOLO_SEVEN));
        }

        @Test
        @DisplayName("실제 카드가 7이 아니면 SOLO_SEVEN은 무효이다")
        void actual_not_seven_is_invalid_solo_seven() {
            List<Card> actual = List.of(card(Suit.SPADE, Rank.EIGHT));
            List<DeclaredCard> declared = List.of(declared(Suit.SPADE, Rank.EIGHT));
            assertFalse(service.validateMeld(actual, declared, MeldType.SOLO_SEVEN));
        }
    }

    @Nested
    @DisplayName("validateMeld - null 및 크기 불일치")
    class ValidateMeldNullAndSizeMismatch {

        @Test
        @DisplayName("actualCards가 null이면 false를 반환한다")
        void null_actual_cards_returns_false() {
            List<DeclaredCard> declared = List.of(declared(Suit.SPADE, Rank.KING));
            assertFalse(service.validateMeld(null, declared, MeldType.SET));
        }

        @Test
        @DisplayName("declaredCards가 null이면 false를 반환한다")
        void null_declared_cards_returns_false() {
            List<Card> actual = List.of(card(Suit.SPADE, Rank.KING));
            assertFalse(service.validateMeld(actual, null, MeldType.SET));
        }

        @Test
        @DisplayName("actual과 declared 크기가 다르면 false를 반환한다")
        void size_mismatch_returns_false() {
            List<Card> actual = List.of(card(Suit.SPADE, Rank.KING), card(Suit.HEART, Rank.KING));
            List<DeclaredCard> declared = List.of(declared(Suit.SPADE, Rank.KING));
            assertFalse(service.validateMeld(actual, declared, MeldType.SET));
        }
    }

    // ==================== isValidBluff ====================

    @Nested
    @DisplayName("isValidBluff")
    class IsValidBluff {

        @Test
        @DisplayName("거짓말 카드가 없으면 SET에서 유효하다")
        void no_bluff_card_valid_for_set() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.KING),
                    card(Suit.HEART, Rank.KING),
                    card(Suit.DIAMOND, Rank.KING)
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            );
            assertTrue(service.isValidBluff(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("거짓말 카드 1장은 SET에서 유효하다")
        void one_bluff_card_valid_for_set() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.KING),
                    card(Suit.HEART, Rank.KING),
                    card(Suit.DIAMOND, Rank.QUEEN) // 블러프
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            );
            assertTrue(service.isValidBluff(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("거짓말 카드 2장은 SET에서 무효이다")
        void two_bluff_cards_invalid_for_set() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.KING),
                    card(Suit.HEART, Rank.QUEEN), // 블러프
                    card(Suit.DIAMOND, Rank.JACK)  // 블러프
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            );
            assertFalse(service.isValidBluff(actual, declared, MeldType.SET));
        }

        @Test
        @DisplayName("SOLO_SEVEN에서 거짓말 0장이어야 유효하다")
        void solo_seven_requires_zero_bluff() {
            List<Card> actual = List.of(card(Suit.SPADE, Rank.SEVEN));
            List<DeclaredCard> declared = List.of(declared(Suit.SPADE, Rank.SEVEN));
            assertTrue(service.isValidBluff(actual, declared, MeldType.SOLO_SEVEN));
        }

        @Test
        @DisplayName("SOLO_SEVEN에서 거짓말 1장도 무효이다")
        void solo_seven_one_bluff_is_invalid() {
            List<Card> actual = List.of(card(Suit.SPADE, Rank.EIGHT));
            List<DeclaredCard> declared = List.of(declared(Suit.SPADE, Rank.SEVEN));
            assertFalse(service.isValidBluff(actual, declared, MeldType.SOLO_SEVEN));
        }

        @Test
        @DisplayName("무늬만 다른 경우도 거짓말로 카운트된다")
        void suit_mismatch_also_counts_as_bluff() {
            List<Card> actual = List.of(
                    card(Suit.SPADE, Rank.KING),
                    card(Suit.HEART, Rank.KING),
                    card(Suit.CLUB, Rank.KING)   // 실제 CLUB
            );
            List<DeclaredCard> declared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING) // 선언 DIAMOND
            );
            // 블러프 1장이므로 유효
            assertTrue(service.isValidBluff(actual, declared, MeldType.SET));
        }
    }

    // ==================== canExtend ====================

    @Nested
    @DisplayName("canExtend - SET")
    class CanExtendSet {

        @Test
        @DisplayName("3장 SET에 같은 숫자 1장 추가는 가능하다 (합계 4장)")
        void extend_three_card_set_with_one_card_is_valid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            );
            Meld meld = buildMeld(MeldType.SET, existingDeclared);

            List<Card> newActual = List.of(card(Suit.CLUB, Rank.KING));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.CLUB, Rank.KING));

            assertTrue(service.canExtend(meld, newActual, newDeclared));
        }

        @Test
        @DisplayName("4장 SET에 1장 더 추가는 불가하다 (합계 5장)")
        void extend_four_card_set_with_one_more_is_invalid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING),
                    declared(Suit.CLUB, Rank.KING)
            );
            Meld meld = buildMeld(MeldType.SET, existingDeclared);

            List<Card> newActual = List.of(card(Suit.SPADE, Rank.KING));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.SPADE, Rank.KING));

            assertFalse(service.canExtend(meld, newActual, newDeclared));
        }

        @Test
        @DisplayName("SET에 다른 숫자 카드 추가는 불가하다")
        void extend_set_with_different_rank_is_invalid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            );
            Meld meld = buildMeld(MeldType.SET, existingDeclared);

            List<Card> newActual = List.of(card(Suit.CLUB, Rank.QUEEN));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.CLUB, Rank.QUEEN));

            assertFalse(service.canExtend(meld, newActual, newDeclared));
        }

        @Test
        @DisplayName("SET 확장에서 거짓말 카드(actual != declared)는 불가하다")
        void extend_set_with_bluff_card_is_invalid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            );
            Meld meld = buildMeld(MeldType.SET, existingDeclared);

            List<Card> newActual = List.of(card(Suit.CLUB, Rank.QUEEN)); // 실제는 QUEEN
            List<DeclaredCard> newDeclared = List.of(declared(Suit.CLUB, Rank.KING)); // 선언은 KING

            assertFalse(service.canExtend(meld, newActual, newDeclared));
        }
    }

    @Nested
    @DisplayName("canExtend - STRAIGHT")
    class CanExtendStraight {

        @Test
        @DisplayName("STRAIGHT 상단에 연속 카드 추가는 가능하다")
        void extend_straight_at_high_end_is_valid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.SPADE, Rank.FOUR),
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX)
            );
            Meld meld = buildMeld(MeldType.STRAIGHT, existingDeclared);

            List<Card> newActual = List.of(card(Suit.SPADE, Rank.SEVEN));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.SPADE, Rank.SEVEN));

            assertTrue(service.canExtend(meld, newActual, newDeclared));
        }

        @Test
        @DisplayName("STRAIGHT 하단에 연속 카드 추가는 가능하다")
        void extend_straight_at_low_end_is_valid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.HEART, Rank.FIVE),
                    declared(Suit.HEART, Rank.SIX),
                    declared(Suit.HEART, Rank.SEVEN)
            );
            Meld meld = buildMeld(MeldType.STRAIGHT, existingDeclared);

            List<Card> newActual = List.of(card(Suit.HEART, Rank.FOUR));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.HEART, Rank.FOUR));

            assertTrue(service.canExtend(meld, newActual, newDeclared));
        }

        @Test
        @DisplayName("STRAIGHT에 다른 무늬 카드 추가는 불가하다")
        void extend_straight_with_different_suit_is_invalid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.SPADE, Rank.FOUR),
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX)
            );
            Meld meld = buildMeld(MeldType.STRAIGHT, existingDeclared);

            List<Card> newActual = List.of(card(Suit.HEART, Rank.SEVEN)); // 다른 무늬
            List<DeclaredCard> newDeclared = List.of(declared(Suit.HEART, Rank.SEVEN));

            assertFalse(service.canExtend(meld, newActual, newDeclared));
        }

        @Test
        @DisplayName("STRAIGHT에 연속되지 않은 카드 추가는 불가하다")
        void extend_straight_with_non_consecutive_card_is_invalid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.SPADE, Rank.FOUR),
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX)
            );
            Meld meld = buildMeld(MeldType.STRAIGHT, existingDeclared);

            List<Card> newActual = List.of(card(Suit.SPADE, Rank.EIGHT)); // 8은 연속이 아님
            List<DeclaredCard> newDeclared = List.of(declared(Suit.SPADE, Rank.EIGHT));

            assertFalse(service.canExtend(meld, newActual, newDeclared));
        }

        @Test
        @DisplayName("STRAIGHT 확장에서 거짓말 카드(actual != declared)는 불가하다")
        void extend_straight_with_bluff_card_is_invalid() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(
                    declared(Suit.SPADE, Rank.FOUR),
                    declared(Suit.SPADE, Rank.FIVE),
                    declared(Suit.SPADE, Rank.SIX)
            );
            Meld meld = buildMeld(MeldType.STRAIGHT, existingDeclared);

            List<Card> newActual = List.of(card(Suit.HEART, Rank.TWO)); // 실제와 선언이 다름
            List<DeclaredCard> newDeclared = List.of(declared(Suit.SPADE, Rank.SEVEN));

            assertFalse(service.canExtend(meld, newActual, newDeclared));
        }
    }

    @Nested
    @DisplayName("canExtend - SOLO_SEVEN")
    class CanExtendSoloSeven {

        @Test
        @DisplayName("SOLO_SEVEN 확장은 항상 불가하다")
        void solo_seven_cannot_be_extended() throws Exception {
            List<DeclaredCard> existingDeclared = List.of(declared(Suit.SPADE, Rank.SEVEN));
            Meld meld = buildMeld(MeldType.SOLO_SEVEN, existingDeclared);

            List<Card> newActual = List.of(card(Suit.HEART, Rank.SEVEN));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.HEART, Rank.SEVEN));

            assertFalse(service.canExtend(meld, newActual, newDeclared));
        }
    }

    @Nested
    @DisplayName("canExtend - null 및 빈 리스트")
    class CanExtendEdgeCases {

        @Test
        @DisplayName("actualCards가 null이면 false를 반환한다")
        void null_actual_returns_false() throws Exception {
            Meld meld = buildMeld(MeldType.SET, List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            ));
            assertFalse(service.canExtend(meld, null, List.of(declared(Suit.CLUB, Rank.KING))));
        }

        @Test
        @DisplayName("빈 actualCards이면 false를 반환한다")
        void empty_actual_returns_false() throws Exception {
            Meld meld = buildMeld(MeldType.SET, List.of(
                    declared(Suit.SPADE, Rank.KING),
                    declared(Suit.HEART, Rank.KING),
                    declared(Suit.DIAMOND, Rank.KING)
            ));
            assertFalse(service.canExtend(meld, List.of(), List.of()));
        }

        // ==================== L-2 null 안전성 픽스 검증 ====================

        @Test
        @DisplayName("canExtend: existingMeld가 null이면 false를 반환한다 (L-2)")
        void null_existing_meld_returns_false() {
            List<Card> newActual = List.of(card(Suit.SPADE, Rank.KING));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.SPADE, Rank.KING));

            assertFalse(service.canExtend(null, newActual, newDeclared));
        }

        @Test
        @DisplayName("canExtendSet: getDeclaredCards()가 빈 리스트를 반환하면 false를 반환한다 (L-2)")
        void canExtendSet_empty_declared_cards_returns_false() {
            Meld mockMeld = Mockito.mock(Meld.class);
            when(mockMeld.getType()).thenReturn(MeldType.SET);
            when(mockMeld.getDeclaredCards()).thenReturn(Collections.emptyList());

            List<Card> newActual = List.of(card(Suit.CLUB, Rank.KING));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.CLUB, Rank.KING));

            assertFalse(service.canExtend(mockMeld, newActual, newDeclared));
        }

        @Test
        @DisplayName("canExtendSet: getDeclaredCards()가 null을 반환하면 false를 반환한다 (L-2)")
        void canExtendSet_null_declared_cards_returns_false() {
            Meld mockMeld = Mockito.mock(Meld.class);
            when(mockMeld.getType()).thenReturn(MeldType.SET);
            when(mockMeld.getDeclaredCards()).thenReturn(null);

            List<Card> newActual = List.of(card(Suit.CLUB, Rank.KING));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.CLUB, Rank.KING));

            assertFalse(service.canExtend(mockMeld, newActual, newDeclared));
        }

        @Test
        @DisplayName("canExtendStraight: getDeclaredCards()가 빈 리스트를 반환하면 false를 반환한다 (L-2)")
        void canExtendStraight_empty_declared_cards_returns_false() {
            Meld mockMeld = Mockito.mock(Meld.class);
            when(mockMeld.getType()).thenReturn(MeldType.STRAIGHT);
            when(mockMeld.getDeclaredCards()).thenReturn(Collections.emptyList());

            List<Card> newActual = List.of(card(Suit.SPADE, Rank.SEVEN));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.SPADE, Rank.SEVEN));

            assertFalse(service.canExtend(mockMeld, newActual, newDeclared));
        }

        @Test
        @DisplayName("canExtendStraight: getDeclaredCards()가 null을 반환하면 false를 반환한다 (L-2)")
        void canExtendStraight_null_declared_cards_returns_false() {
            Meld mockMeld = Mockito.mock(Meld.class);
            when(mockMeld.getType()).thenReturn(MeldType.STRAIGHT);
            when(mockMeld.getDeclaredCards()).thenReturn(null);

            List<Card> newActual = List.of(card(Suit.SPADE, Rank.SEVEN));
            List<DeclaredCard> newDeclared = List.of(declared(Suit.SPADE, Rank.SEVEN));

            assertFalse(service.canExtend(mockMeld, newActual, newDeclared));
        }
    }
}
