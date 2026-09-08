package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.access.TokenService;
import dev.tuiop.filedrop.drop.internal.dto.FileDropDetailsResponse;
import dev.tuiop.filedrop.drop.internal.dto.UpdateExpirationRequest;
import dev.tuiop.filedrop.drop.internal.dto.UpdateMaxDownloadsRequest;
import dev.tuiop.filedrop.drop.internal.exception.FileDropNotEditableException;
import dev.tuiop.filedrop.drop.internal.exception.FileDropNotFoundException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidMaxDownloadsException;
import dev.tuiop.filedrop.drop.internal.validation.DropRequestValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDropManagementService {

    private final FileDropRepository fileDropRepository;
    private final DropRequestValidator dropRequestValidator;
    private final TokenService tokenService;

    public FileDropDetailsResponse getDetails(UUID id, String managementToken) {
        validateToken(managementToken);
        String managementTokenHash = tokenService.hashToken(managementToken);

        FileDrop drop = fileDropRepository
                .findByIdAndManagementTokenHash(id, managementTokenHash)
                .orElseThrow(FileDropNotFoundException::new);

        return toDetailsResponse(drop);
    }

    @Transactional
    public FileDropDetailsResponse updateExpiration(
            UUID id,
            String managementToken,
            UpdateExpirationRequest request
    ) {
        FileDrop drop = findByIdAndManagementTokenHashForUpdate(id, managementToken);
        ensureEditable(drop);
        dropRequestValidator.validateExpiration(request.expiresAt());
        drop.changeExpiration(request.expiresAt());

        log.info(
                "File drop expiration updated dropId={} expiresAt={}",
                id,
                request.expiresAt()
        );

        return toDetailsResponse(drop);
    }

    @Transactional
    public FileDropDetailsResponse updateMaxDownloads(
            UUID id,
            String managementToken,
            UpdateMaxDownloadsRequest request
    ) {
        FileDrop drop = findByIdAndManagementTokenHashForUpdate(id, managementToken);
        ensureEditable(drop);
        dropRequestValidator.validateMaxDownloads(request.maxDownloads());

        if (request.maxDownloads() <= drop.getDownloadCount()) {
            throw new InvalidMaxDownloadsException(
                    "Maximum downloads must exceed the current download count of %d."
                            .formatted(drop.getDownloadCount())
            );
        }

        drop.changeMaxDownloads(request.maxDownloads());

        log.info(
                "File drop maximum downloads updated dropId={} maxDownloads={}",
                id,
                request.maxDownloads()
        );

        return toDetailsResponse(drop);
    }

    @Transactional
    public void requestDeletion(UUID id, String managementToken) {
        FileDrop drop = findByIdAndManagementTokenHashForUpdate(id, managementToken);

        if (drop.getStatus() == FileDropStatus.DELETED
                || drop.getStatus() == FileDropStatus.DELETION_PENDING) {
            log.debug(
                    "Ignoring repeated deletion request dropId={} status={}",
                    id,
                    drop.getStatus()
            );
            return;
        }

        drop.markDeletionPending();
        fileDropRepository.save(drop);

        log.info("File drop deletion requested dropId={}", id);
    }

    private FileDrop findByIdAndManagementTokenHashForUpdate(
            UUID id,
            String managementToken
    ) {
        validateToken(managementToken);
        String managementTokenHash = tokenService.hashToken(managementToken);

        return fileDropRepository
                .findByIdAndManagementTokenHashForUpdate(id, managementTokenHash)
                .orElseThrow(FileDropNotFoundException::new);
    }

    private void validateToken(String token) {
        if (!tokenService.isValidFormat(token)) {
            throw new FileDropNotFoundException();
        }
    }

    private void ensureEditable(FileDrop drop) {
        if (drop.getStatus() != FileDropStatus.AVAILABLE
                || drop.isExpired(Instant.now())) {
            throw new FileDropNotEditableException();
        }
    }

    private FileDropDetailsResponse toDetailsResponse(FileDrop drop) {
        return new FileDropDetailsResponse(
                drop.getId(),
                drop.getOriginalFileName(),
                drop.getDetectedContentType(),
                drop.getSize(),
                drop.getCreatedAt(),
                drop.getExpiresAt(),
                drop.getDeletedAt(),
                drop.getMaxDownloads(),
                drop.getDownloadCount(),
                drop.getDownloadsRemaining(),
                drop.getStatus(),
                drop.getPasswordHash() != null
        );
    }
}
