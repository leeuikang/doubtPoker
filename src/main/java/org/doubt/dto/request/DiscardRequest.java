package org.doubt.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.doubt.dto.Card;

/** 버리기 요청: 손에서 카드 1장을 버림 더미로 */
public record DiscardRequest(
        @NotNull @Valid Card card
) {
}
