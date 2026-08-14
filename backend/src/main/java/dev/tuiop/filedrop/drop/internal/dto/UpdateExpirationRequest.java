package dev.tuiop.filedrop.drop.internal.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record UpdateExpirationRequest(
        @NotNull Instant expiresAt
) {
}
