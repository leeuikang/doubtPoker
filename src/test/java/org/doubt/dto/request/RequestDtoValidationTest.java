package org.doubt.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.doubt.constant.DrawSource;
import org.doubt.constant.MeldType;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // --- 헬퍼 메서드 ---

    private Card validCard() {
        return new Card(Suit.SPADE, Rank.ACE);
    }

    private DeclaredCard validDeclaredCard() {
        return new DeclaredCard(Suit.HEART, Rank.KING);
    }

    private List<Card> validCardList(int size) {
        List<Card> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(validCard());
        }
        return list;
    }

    private List<DeclaredCard> validDeclaredCardList(int size) {
        List<DeclaredCard> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(validDeclaredCard());
        }
        return list;
    }

    // =========================================================
    // DrawRequest
    // =========================================================

    @Nested
    @DisplayName("DrawRequest")
    class DrawRequestTest {

        @Test
        @DisplayName("source 가 null 이면 위반 1건 발생")
        void nullSource_shouldProduceOneViolation() {
            DrawRequest req = new DrawRequest(null);

            Set<ConstraintViolation<DrawRequest>> violations = validator.validate(req);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString())
                    .isEqualTo("source");
        }

        @Test
        @DisplayName("유효한 source → 위반 없음")
        void validSource_shouldProduceNoViolations() {
            DrawRequest req = new DrawRequest(DrawSource.STOCK);

            Set<ConstraintViolation<DrawRequest>> violations = validator.validate(req);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("DISCARD source 도 유효 → 위반 없음")
        void discardSource_shouldProduceNoViolations() {
            DrawRequest req = new DrawRequest(DrawSource.DISCARD);

            Set<ConstraintViolation<DrawRequest>> violations = validator.validate(req);

            assertThat(violations).isEmpty();
        }
    }

    // =========================================================
    // MeldRequest
    // =========================================================

    @Nested
    @DisplayName("MeldRequest")
    class MeldRequestTests {

        @Test
        @DisplayName("actualCards 가 null 이면 위반 발생")
        void nullActualCards_shouldProduceViolation() {
            MeldRequest req = new MeldRequest(null, validDeclaredCardList(1), MeldType.SET);

            Set<ConstraintViolation<MeldRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("actualCards"));
        }

        @Test
        @DisplayName("actualCards 가 빈 리스트이면 위반 발생 (@Size min=3)")
        void emptyActualCards_shouldProduceViolation() {
            MeldRequest req = new MeldRequest(
                    Collections.emptyList(), validDeclaredCardList(1), MeldType.SET);

            Set<ConstraintViolation<MeldRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("actualCards"));
        }

        @Test
        @DisplayName("actualCards 크기가 14이면 위반 발생 (@Size max=13)")
        void oversizedActualCards_shouldProduceViolation() {
            MeldRequest req = new MeldRequest(
                    validCardList(14), validDeclaredCardList(1), MeldType.SET);

            Set<ConstraintViolation<MeldRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("actualCards"));
        }

        @Test
        @DisplayName("type 이 null 이면 위반 발생")
        void nullType_shouldProduceViolation() {
            MeldRequest req = new MeldRequest(validCardList(1), validDeclaredCardList(1), null);

            Set<ConstraintViolation<MeldRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
        }

        @Test
        @DisplayName("actualCards 리스트 안에 null 카드가 있으면 위반 발생 (cascaded @Valid)")
        void nullCardInActualCards_shouldProduceViolation() {
            List<Card> listWithNull = new ArrayList<>();
            listWithNull.add(null);

            MeldRequest req = new MeldRequest(listWithNull, validDeclaredCardList(1), MeldType.SET);

            Set<ConstraintViolation<MeldRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("Card 의 suit 이 null 이면 카스케이드 @Valid 로 위반 발생")
        void cardWithNullSuit_shouldProduceViolation() {
            List<Card> listWithInvalidCard = List.of(new Card(null, Rank.ACE));

            MeldRequest req = new MeldRequest(listWithInvalidCard, validDeclaredCardList(1), MeldType.STRAIGHT);

            Set<ConstraintViolation<MeldRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("suit"));
        }

        @Test
        @DisplayName("정상 MeldRequest → 위반 없음")
        void validMeldRequest_shouldProduceNoViolations() {
            MeldRequest req = new MeldRequest(
                    validCardList(3), validDeclaredCardList(3), MeldType.SET);

            Set<ConstraintViolation<MeldRequest>> violations = validator.validate(req);

            assertThat(violations).isEmpty();
        }
    }

    // =========================================================
    // ExtendRequest
    // =========================================================

    @Nested
    @DisplayName("ExtendRequest")
    class ExtendRequestTests {

        @Test
        @DisplayName("meldId 가 blank 이면 위반 발생")
        void blankMeldId_shouldProduceViolation() {
            ExtendRequest req = new ExtendRequest("  ", validCardList(1), validDeclaredCardList(1));

            Set<ConstraintViolation<ExtendRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("meldId"));
        }

        @Test
        @DisplayName("meldId 가 빈 문자열이면 위반 발생")
        void emptyMeldId_shouldProduceViolation() {
            ExtendRequest req = new ExtendRequest("", validCardList(1), validDeclaredCardList(1));

            Set<ConstraintViolation<ExtendRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("meldId"));
        }

        @Test
        @DisplayName("actualCards 가 빈 리스트이면 위반 발생")
        void emptyActualCards_shouldProduceViolation() {
            ExtendRequest req = new ExtendRequest("meld-1", Collections.emptyList(), validDeclaredCardList(1));

            Set<ConstraintViolation<ExtendRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("actualCards"));
        }

        @Test
        @DisplayName("actualCards 크기가 14이면 위반 발생")
        void oversizedActualCards_shouldProduceViolation() {
            ExtendRequest req = new ExtendRequest("meld-1", validCardList(14), validDeclaredCardList(1));

            Set<ConstraintViolation<ExtendRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("actualCards"));
        }

        @Test
        @DisplayName("actualCards 안에 suit 이 null 인 카드 → 카스케이드 위반 발생")
        void nullSuitCardInActualCards_shouldProduceViolation() {
            List<Card> listWithInvalidCard = List.of(new Card(null, Rank.QUEEN));
            ExtendRequest req = new ExtendRequest("meld-1", listWithInvalidCard, validDeclaredCardList(1));

            Set<ConstraintViolation<ExtendRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("suit"));
        }

        @Test
        @DisplayName("정상 ExtendRequest → 위반 없음")
        void validExtendRequest_shouldProduceNoViolations() {
            ExtendRequest req = new ExtendRequest("meld-1", validCardList(2), validDeclaredCardList(2));

            Set<ConstraintViolation<ExtendRequest>> violations = validator.validate(req);

            assertThat(violations).isEmpty();
        }
    }

    // =========================================================
    // DiscardRequest
    // =========================================================

    @Nested
    @DisplayName("DiscardRequest")
    class DiscardRequestTests {

        @Test
        @DisplayName("card 가 null 이면 위반 발생")
        void nullCard_shouldProduceViolation() {
            DiscardRequest req = new DiscardRequest(null);

            Set<ConstraintViolation<DiscardRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("card"));
        }

        @Test
        @DisplayName("card 의 suit 이 null 이면 카스케이드 @Valid 로 위반 발생")
        void cardWithNullSuit_shouldProduceViolation() {
            DiscardRequest req = new DiscardRequest(new Card(null, Rank.TEN));

            Set<ConstraintViolation<DiscardRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("suit"));
        }

        @Test
        @DisplayName("card 의 rank 가 null 이면 카스케이드 @Valid 로 위반 발생")
        void cardWithNullRank_shouldProduceViolation() {
            DiscardRequest req = new DiscardRequest(new Card(Suit.CLUB, null));

            Set<ConstraintViolation<DiscardRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("rank"));
        }

        @Test
        @DisplayName("정상 DiscardRequest → 위반 없음")
        void validDiscardRequest_shouldProduceNoViolations() {
            DiscardRequest req = new DiscardRequest(new Card(Suit.DIAMOND, Rank.SEVEN));

            Set<ConstraintViolation<DiscardRequest>> violations = validator.validate(req);

            assertThat(violations).isEmpty();
        }
    }

    // =========================================================
    // DoubtRequest
    // =========================================================

    @Nested
    @DisplayName("DoubtRequest")
    class DoubtRequestTests {

        @Test
        @DisplayName("meldId 가 blank 이면 위반 발생")
        void blankMeldId_shouldProduceViolation() {
            DoubtRequest req = new DoubtRequest("  ");

            Set<ConstraintViolation<DoubtRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("meldId"));
        }

        @Test
        @DisplayName("meldId 가 공백만 있어도 위반 발생 (whitespace-only)")
        void whitespaceOnlyMeldId_shouldProduceViolation() {
            DoubtRequest req = new DoubtRequest("\t\n ");

            Set<ConstraintViolation<DoubtRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("meldId"));
        }

        @Test
        @DisplayName("meldId 가 null 이면 위반 발생")
        void nullMeldId_shouldProduceViolation() {
            DoubtRequest req = new DoubtRequest(null);

            Set<ConstraintViolation<DoubtRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("meldId"));
        }

        @Test
        @DisplayName("정상 DoubtRequest → 위반 없음")
        void validDoubtRequest_shouldProduceNoViolations() {
            DoubtRequest req = new DoubtRequest("meld-abc-123");

            Set<ConstraintViolation<DoubtRequest>> violations = validator.validate(req);

            assertThat(violations).isEmpty();
        }
    }

    // =========================================================
    // RevealBluffRequest
    // =========================================================

    @Nested
    @DisplayName("RevealBluffRequest")
    class RevealBluffRequestTests {

        @Test
        @DisplayName("meldId 가 blank 이면 위반 발생")
        void blankMeldId_shouldProduceViolation() {
            RevealBluffRequest req = new RevealBluffRequest("  ");

            Set<ConstraintViolation<RevealBluffRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("meldId"));
        }

        @Test
        @DisplayName("meldId 가 공백만 있어도 위반 발생 (whitespace-only)")
        void whitespaceOnlyMeldId_shouldProduceViolation() {
            RevealBluffRequest req = new RevealBluffRequest("\t\n ");

            Set<ConstraintViolation<RevealBluffRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("meldId"));
        }

        @Test
        @DisplayName("meldId 가 null 이면 위반 발생")
        void nullMeldId_shouldProduceViolation() {
            RevealBluffRequest req = new RevealBluffRequest(null);

            Set<ConstraintViolation<RevealBluffRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("meldId"));
        }

        @Test
        @DisplayName("정상 RevealBluffRequest → 위반 없음")
        void validRevealBluffRequest_shouldProduceNoViolations() {
            RevealBluffRequest req = new RevealBluffRequest("meld-xyz-456");

            Set<ConstraintViolation<RevealBluffRequest>> violations = validator.validate(req);

            assertThat(violations).isEmpty();
        }
    }

    // =========================================================
    // Card (독립 검증)
    // =========================================================

    @Nested
    @DisplayName("Card")
    class CardTests {

        @Test
        @DisplayName("suit, rank 모두 유효 → 위반 없음")
        void validCard_shouldProduceNoViolations() {
            Card card = new Card(Suit.HEART, Rank.SEVEN);

            Set<ConstraintViolation<Card>> violations = validator.validate(card);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("suit 이 null → 위반 발생")
        void nullSuit_shouldProduceViolation() {
            Card card = new Card(null, Rank.ACE);

            Set<ConstraintViolation<Card>> violations = validator.validate(card);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("suit");
        }

        @Test
        @DisplayName("rank 가 null → 위반 발생")
        void nullRank_shouldProduceViolation() {
            Card card = new Card(Suit.CLUB, null);

            Set<ConstraintViolation<Card>> violations = validator.validate(card);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("rank");
        }

        @Test
        @DisplayName("suit, rank 모두 null → 위반 2건 발생")
        void bothNull_shouldProduceTwoViolations() {
            Card card = new Card(null, null);

            Set<ConstraintViolation<Card>> violations = validator.validate(card);

            assertThat(violations).hasSize(2);
        }
    }

    // =========================================================
    // DeclaredCard (독립 검증)
    // =========================================================

    @Nested
    @DisplayName("DeclaredCard")
    class DeclaredCardTests {

        @Test
        @DisplayName("declaredSuit, declaredRank 모두 유효 → 위반 없음")
        void validDeclaredCard_shouldProduceNoViolations() {
            DeclaredCard card = new DeclaredCard(Suit.DIAMOND, Rank.KING);

            Set<ConstraintViolation<DeclaredCard>> violations = validator.validate(card);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("declaredSuit 이 null → 위반 발생")
        void nullDeclaredSuit_shouldProduceViolation() {
            DeclaredCard card = new DeclaredCard(null, Rank.JACK);

            Set<ConstraintViolation<DeclaredCard>> violations = validator.validate(card);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("declaredSuit");
        }

        @Test
        @DisplayName("declaredRank 가 null → 위반 발생")
        void nullDeclaredRank_shouldProduceViolation() {
            DeclaredCard card = new DeclaredCard(Suit.SPADE, null);

            Set<ConstraintViolation<DeclaredCard>> violations = validator.validate(card);

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("declaredRank");
        }
    }
}
