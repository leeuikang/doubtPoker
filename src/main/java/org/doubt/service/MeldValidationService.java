package org.doubt.service;

import org.doubt.constant.MeldType;
import org.doubt.constant.Rank;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;
import org.doubt.dto.Meld;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 멜드 유효성 검증 서비스
 * - 세트·스트레이트·단독7 규칙 검증
 * - 거짓말 멜드 규칙 검증 (최대 1장 허위)
 * - 확장 가능 여부 검증
 */
@Service
public class MeldValidationService {

    /**
     * 새 멜드가 유효한지 검증
     * - 선언 카드가 해당 타입의 구조 규칙을 만족하는지 확인
     * - 실제 카드와의 거짓말 규칙(최대 1장 허위)을 확인
     *
     * @param actualCards   실제 카드
     * @param declaredCards 선언 카드 (거짓말 없으면 동일)
     * @param type          멜드 종류
     */
    public boolean validateMeld(List<Card> actualCards, List<DeclaredCard> declaredCards, MeldType type) {
        if (actualCards == null || declaredCards == null) return false;
        if (actualCards.size() != declaredCards.size()) return false;

        return switch (type) {
            case SOLO_SEVEN -> isValidSoloSeven(actualCards, declaredCards);
            case SET -> isValidSet(declaredCards) && isValidBluff(actualCards, declaredCards, MeldType.SET);
            case STRAIGHT -> isValidStraight(declaredCards) && isValidBluff(actualCards, declaredCards, MeldType.STRAIGHT);
        };
    }

    /**
     * 기존 멜드에 카드를 붙일 수 있는지 검증
     * - 구조적 연속성 및 사이즈 제한 확인
     * - 확장 카드는 거짓말 없이 실제 카드와 선언 카드가 일치해야 함
     *
     * @param existingMeld  테이블 위 멜드
     * @param actualCards   붙이려는 실제 카드
     * @param declaredCards 붙이려는 선언 카드
     */
    public boolean canExtend(Meld existingMeld, List<Card> actualCards, List<DeclaredCard> declaredCards) {
        if (existingMeld == null || actualCards == null || declaredCards == null) return false;
        if (actualCards.size() != declaredCards.size() || actualCards.isEmpty()) return false;

        // 확장 카드는 실제 카드와 선언 카드가 일치해야 함 (확장에는 거짓말 불가)
        for (int i = 0; i < actualCards.size(); i++) {
            Card actual = actualCards.get(i);
            DeclaredCard declared = declaredCards.get(i);
            if (actual.rank() != declared.declaredRank() || actual.suit() != declared.declaredSuit()) {
                return false;
            }
        }

        return switch (existingMeld.getType()) {
            case SOLO_SEVEN -> false; // 단독 7은 확장 불가
            case SET -> canExtendSet(existingMeld, declaredCards);
            case STRAIGHT -> canExtendStraight(existingMeld, declaredCards);
        };
    }

    /**
     * 거짓말 멜드 규칙 위반 여부 검증
     * 규칙: 멜드 내 허위 카드 최대 1장
     * (SOLO_SEVEN은 거짓말 불가)
     */
    public boolean isValidBluff(List<Card> actualCards, List<DeclaredCard> declaredCards, MeldType type) {
        if (type == MeldType.SOLO_SEVEN) {
            return countBluffs(actualCards, declaredCards) == 0;
        }
        return countBluffs(actualCards, declaredCards) <= 1;
    }

    // --- private 검증 헬퍼 ---

    private boolean isValidSoloSeven(List<Card> actualCards, List<DeclaredCard> declaredCards) {
        if (actualCards.size() != 1) return false;
        // 실제 카드와 선언 카드 모두 7이어야 하며 거짓말 불가
        return actualCards.get(0).rank() == Rank.SEVEN
                && declaredCards.get(0).declaredRank() == Rank.SEVEN
                && countBluffs(actualCards, declaredCards) == 0;
    }

    /** 선언 카드가 유효한 SET인지 확인: 같은 숫자 3~4장 */
    private boolean isValidSet(List<DeclaredCard> declaredCards) {
        int size = declaredCards.size();
        if (size < 3 || size > 4) return false;
        Rank targetRank = declaredCards.get(0).declaredRank();
        return declaredCards.stream().allMatch(c -> c.declaredRank() == targetRank);
    }

    /** 선언 카드가 유효한 STRAIGHT인지 확인: 같은 무늬의 연속된 숫자 3장 이상 */
    private boolean isValidStraight(List<DeclaredCard> declaredCards) {
        if (declaredCards.size() < 3) return false;
        // 모두 같은 무늬
        var firstSuit = declaredCards.get(0).declaredSuit();
        if (declaredCards.stream().anyMatch(c -> c.declaredSuit() != firstSuit)) return false;
        return isConsecutiveStraight(declaredCards);
    }

    /**
     * 선언 카드의 숫자가 연속인지 확인
     * A는 K 다음(14) 또는 2 이전(1)으로만 사용 가능 (Q-K-A 또는 A-2-3)
     */
    private boolean isConsecutiveStraight(List<DeclaredCard> declaredCards) {
        List<Integer> ranks = declaredCards.stream()
                .map(c -> c.declaredRank().getBasePoint())
                .sorted()
                .collect(Collectors.toList());

        // 일반 순서 (A=1)
        if (isConsecutive(ranks)) return true;

        // A를 14로 취급 (A + K 조합: Q-K-A 같은 경우)
        if (ranks.contains(1)) {
            List<Integer> aceHigh = ranks.stream()
                    .map(r -> r == 1 ? 14 : r)
                    .sorted()
                    .collect(Collectors.toList());
            return isConsecutive(aceHigh);
        }
        return false;
    }

    private boolean isConsecutive(List<Integer> sortedRanks) {
        for (int i = 1; i < sortedRanks.size(); i++) {
            if (sortedRanks.get(i) - sortedRanks.get(i - 1) != 1) return false;
        }
        return true;
    }

    /** SET 확장: 같은 숫자, 합산 최대 4장 */
    private boolean canExtendSet(Meld existingMeld, List<DeclaredCard> newDeclared) {
        List<DeclaredCard> existing = existingMeld.getDeclaredCards();
        if (existing == null || existing.isEmpty()) return false;
        int newTotal = existing.size() + newDeclared.size();
        if (newTotal > 4) return false;

        Rank meldRank = existing.get(0).declaredRank();
        return newDeclared.stream().allMatch(c -> c.declaredRank() == meldRank);
    }

    /** STRAIGHT 확장: 같은 무늬, 기존 + 신규 선언 카드가 연속 수열 유지 */
    private boolean canExtendStraight(Meld existingMeld, List<DeclaredCard> newDeclared) {
        List<DeclaredCard> existing = existingMeld.getDeclaredCards();
        if (existing == null || existing.isEmpty()) return false;
        var meldSuit = existing.get(0).declaredSuit();

        // 새 카드 무늬 확인
        if (newDeclared.stream().anyMatch(c -> c.declaredSuit() != meldSuit)) return false;

        // 기존 + 신규 합쳐서 연속 수열인지 확인
        List<DeclaredCard> combined = new ArrayList<>(existing);
        combined.addAll(newDeclared);
        return isConsecutiveStraight(combined);
    }

    /** actualCards와 declaredCards 간 불일치(거짓말) 장 수를 반환 */
    private long countBluffs(List<Card> actualCards, List<DeclaredCard> declaredCards) {
        long count = 0;
        for (int i = 0; i < actualCards.size(); i++) {
            Card actual = actualCards.get(i);
            DeclaredCard declared = declaredCards.get(i);
            if (actual.rank() != declared.declaredRank() || actual.suit() != declared.declaredSuit()) {
                count++;
            }
        }
        return count;
    }
}
