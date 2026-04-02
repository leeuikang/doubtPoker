package org.doubt.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 거짓말 지목 요청
 * 조건: 자신의 턴, 직전 멜드만 지목 가능
 * - meldId: 지목할 멜드 ID
 */
public record DoubtRequest(
        @NotBlank String meldId
) {
}
