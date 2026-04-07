package org.doubt.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.doubt.dto.Card;
import org.doubt.dto.DeclaredCard;

import java.util.List;

/**
 * 기존 멜드에 카드 붙이기 요청
 * - meldId       : 확장할 테이블 위 멜드 ID
 * - actualCards  : 실제 붙이는 카드 목록
 * - declaredCards: 선언하는 카드 목록 (거짓말 불가 — actual과 declared가 일치해야 함)
 */
public record ExtendRequest(
        @NotBlank String meldId,
        @NotNull @Size(min = 1, max = 13) List<@NotNull @Valid Card> actualCards,
        @NotNull @Size(min = 1, max = 13) List<@NotNull @Valid DeclaredCard> declaredCards
) {
}
