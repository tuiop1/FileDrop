package dev.tuiop.filedrop.drop.dto;

import dev.tuiop.filedrop.drop.internal.FileDropStatus;

import java.time.Instant;
import java.util.UUID;

public record FileDropResponse(
		UUID id,
		String originalFileName,
		String detectedContentType,
		Long size,
		String sha256,
		Instant createdAt,
		Instant expiresAt,
		Instant deletedAt,
		Integer maxDownloads,
		Integer downloadCount,
		FileDropStatus status) {
}
