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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GL-C4 버그 수정 검증 테스트
 * canExtendStraight 이 기존 확장 카드를 포함해 중복 위치를 방지하는지 확인
 */
@DisplayName("GL-C4: canExtendStraight — 기존 확장 카드 포함 중복 방지")
class GlC4FixTest {

    private MeldValidationService meldValidationService;

    @BeforeEach
    void setUp() {
        meldValidationService = new MeldValidationService();
    }

    /**
     * STRAIGHT [5♥, 6♥, 7♥] 멜드를 생성하는 헬퍼.
     * actualCards 와 declaredCards 가 동일한(거짓말 없는) 멜드.
     */
    private Meld buildStraightMeld(String meldId, String ownerId) {
        List<Card> actualCards = List.of(
                new Card(Suit.HEART, Rank.FIVE),
                new Card(Suit.HEART, Rank.SIX),
                new Card(Suit.HEART, Rank.SEVEN)
        );
        List<DeclaredCard> declaredCards = List.of(
                new DeclaredCard(Suit.HEART, Rank.FIVE),
                new DeclaredCard(Suit.HEART, Rank.SIX),
                new DeclaredCard(Suit.HEART, Rank.SEVEN)
        );
        return Meld.create(meldId, ownerId, MeldType.STRAIGHT, actualCards, declaredCards, false);
    }

    @Nested
    @DisplayName("canExtend — STRAIGHT 확장 검증")
    class CanExtendStraight {

        @Test
        @DisplayName("이미 4♥ 확장이 있을 때 같은 4♥를 다시 확장하려 하면 false를 반환한다")
        void canExtend_straight_withExistingExtensions_preventsDuplicatePosition() {
            // STRAIGHT [5♥, 6♥, 7♥] 생성
            Meld meld = buildStraightMeld("meld-1", "owner");

            // "extender"가 이미 4♥를 확장으로 붙인 상태
            List<Card> existingExtension = new ArrayList<>(List.of(new Card(Suit.HEART, Rank.FOUR)));
            meld.getExtensions().put("extender", existingExtension);

            // 동일한 4♥를 다시 확장 시도
            List<Card> newActual = List.of(new Card(Suit.HEART, Rank.FOUR));
            List<DeclaredCard> newDeclared = List.of(new DeclaredCard(Suit.HEART, Rank.FOUR));

            boolean result = meldValidationService.canExtend(meld, newActual, newDeclared);

            // GL-C4: 기존 확장 카드가 combined 에 포함되므로 [4,4,5,6,7]이 되어 연속 판정 실패
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("이미 4♥ 확장이 있을 때 8♥를 확장하면 true를 반환한다")
        void canExtend_straight_withExistingExtensions_allowsValidExtension() {
            // STRAIGHT [5♥, 6♥, 7♥] 생성
            Meld meld = buildStraightMeld("meld-1", "owner");

            // "extender"가 이미 4♥를 확장으로 붙인 상태 (combined: [4,5,6,7])
            List<Card> existingExtension = new ArrayList<>(List.of(new Card(Suit.HEART, Rank.FOUR)));
            meld.getExtensions().put("extender", existingExtension);

            // 8♥를 확장 시도 → combined: [4,5,6,7,8] → 연속 수열
            List<Card> newActual = List.of(new Card(Suit.HEART, Rank.EIGHT));
            List<DeclaredCard> newDeclared = List.of(new DeclaredCard(Suit.HEART, Rank.EIGHT));

            boolean result = meldValidationService.canExtend(meld, newActual, newDeclared);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("확장 카드가 없는 기본 STRAIGHT [5♥,6♥,7♥]에 8♥를 붙이면 true를 반환한다")
        void canExtend_straight_withNoExtensions_normalValidation() {
            // STRAIGHT [5♥, 6♥, 7♥] — 확장 없음
            Meld meld = buildStraightMeld("meld-1", "owner");

            List<Card> newActual = List.of(new Card(Suit.HEART, Rank.EIGHT));
            List<DeclaredCard> newDeclared = List.of(new DeclaredCard(Suit.HEART, Rank.EIGHT));

            boolean result = meldValidationService.canExtend(meld, newActual, newDeclared);

            assertThat(result).isTrue();
        }
    }
}
