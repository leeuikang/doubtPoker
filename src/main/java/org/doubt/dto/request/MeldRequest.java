package org.doubt.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
        @NotNull @Size(min = 1, max = 13) List<@NotNull @Valid Card> actualCards,
        @NotNull @Size(min = 1, max = 13) List<@NotNull @Valid DeclaredCard> declaredCards,
        @NotNull MeldType type
) {
}
