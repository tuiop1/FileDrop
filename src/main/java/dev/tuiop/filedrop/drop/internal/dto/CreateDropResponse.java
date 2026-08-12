package dev.tuiop.filedrop.drop.internal.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CreateDropResponse(
        UUID id,
        String downloadUrl,
        String managementToken,
        Instant expiresAt
) {
}
