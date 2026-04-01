package org.doubt.dto.request;

import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;

import java.util.List;

/**
 * 기존 멜드에 카드 붙이기 요청
 * - meldId       : 확장할 테이블 위 멜드 ID
 * - actualCards  : 실제 붙이는 카드 목록
 * - declaredCards: 선언하는 카드 목록 (거짓말 포함 가능)
 */
public record ExtendRequest(
        String meldId,
        List<Card> actualCards,
        List<DeclaredCard> declaredCards
) {
}