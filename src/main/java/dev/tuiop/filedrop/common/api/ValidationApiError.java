package dev.tuiop.filedrop.common.api;

import java.time.Instant;
import java.util.Map;

public record ValidationApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> errors
) {
    public static ValidationApiError of(
            int status,
            String code,
            String message,
            String path,
            Map<String, String> errors
    ) {
        return new ValidationApiError(Instant.now(), status, code, message, path, errors);
    }
}
