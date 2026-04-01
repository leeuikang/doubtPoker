package org.doubt.dto.request;

import org.doubt.constant.MeldType;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;

import java.util.List;

/**
 * 새 멜드 내려놓기 요청
 * - actualCards  : 실제 손에서 내는 카드 목록
 * - declaredCards: 선언하는 카드 목록 (거짓말 시 일부 다름)
 * - type         : SET / STRAIGHT / SOLO_SEVEN
 */
public record MeldRequest(
        List<Card> actualCards,
        List<DeclaredCard> declaredCards,
        MeldType type
) {
}