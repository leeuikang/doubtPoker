package org.doubt.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 거짓말 자진 공개 요청
 * 조건: 자신의 턴, 본인이 낸 거짓말 멜드만 공개 가능
 * 해당 멜드에 확장 카드를 붙인 플레이어들은 카드 1장씩 드로우
 * - meldId: 공개할 멜드 ID
 */
public record RevealBluffRequest(
        @NotBlank String meldId
) {
}
