package dev.tuiop.filedrop.drop.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateDropRequest(
        @Min(1)
        @Max(100)
        @NotNull
        Integer maxDownloads,

        @NotNull
        @Future
        Instant expiresAt,

        String password



) {
}
