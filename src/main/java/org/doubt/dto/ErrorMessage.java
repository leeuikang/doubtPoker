package org.doubt.dto;

import java.time.LocalDateTime;
import static java.util.UUID.randomUUID;

public record ErrorMessage(String errorCode, String message, String timestamp, String traceId) {
    public static ErrorMessage of(String code, String message) {
        return new ErrorMessage(
                code,
                message,
                LocalDateTime.now().toString(),
                randomUUID().toString()
        );
    }
}
