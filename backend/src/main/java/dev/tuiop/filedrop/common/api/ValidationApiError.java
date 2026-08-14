package dev.tuiop.filedrop.common.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ValidationApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, List<String>> errors
) {
    public static ValidationApiError of(
            int status,
            String code,
            String message,
            String path,
            Map<String, List<String>> errors
    ) {
        return new ValidationApiError(Instant.now(), status, code, message, path, errors);
    }
}
