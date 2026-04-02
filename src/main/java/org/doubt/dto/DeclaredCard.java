package org.doubt.dto;

import jakarta.validation.constraints.NotNull;
import org.doubt.constant.Rank;
import org.doubt.constant.Suit;

/**
 * 거짓말 멜드 시 선언하는 카드 정보 (플레이어가 주장하는 무늬·숫자)
 */
public record DeclaredCard(
        @NotNull Suit declaredSuit,
        @NotNull Rank declaredRank
) {
}