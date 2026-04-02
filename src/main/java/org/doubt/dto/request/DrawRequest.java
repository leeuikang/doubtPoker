package org.doubt.dto.request;

import jakarta.validation.constraints.NotNull;
import org.doubt.constant.DrawSource;

/** 드로우 요청: 스톡 또는 버림 더미에서 카드 1장 */
public record DrawRequest(
        @NotNull DrawSource source
) {
}
