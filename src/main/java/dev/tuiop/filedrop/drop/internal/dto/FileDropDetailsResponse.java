package dev.tuiop.filedrop.drop.internal.dto;

import dev.tuiop.filedrop.drop.internal.FileDropStatus;

import java.time.Instant;
import java.util.UUID;

public record FileDropDetailsResponse(
        UUID id,
        String originalFileName,
        String contentType,
        long size,
        Instant createdAt,
        Instant expiresAt,
        Instant deletedAt,
        int maxDownloads,
        int downloadCount,
        int downloadsRemaining,
        FileDropStatus status,
        boolean passwordProtected
) {
}
