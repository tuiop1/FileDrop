package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.access.PasswordService;
import dev.tuiop.filedrop.access.TokenService;
import dev.tuiop.filedrop.crypto.EncryptedFile;
import dev.tuiop.filedrop.crypto.FileEncryptionService;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropResponse;
import dev.tuiop.filedrop.drop.internal.dto.DropDownloadResult;
import dev.tuiop.filedrop.drop.internal.dto.FileDropDetailsResponse;
import dev.tuiop.filedrop.drop.internal.dto.UpdateExpirationRequest;
import dev.tuiop.filedrop.drop.internal.dto.UpdateMaxDownloadsRequest;
import dev.tuiop.filedrop.drop.internal.exception.DownloadLimitExceededException;
import dev.tuiop.filedrop.drop.internal.exception.DropCreationException;
import dev.tuiop.filedrop.drop.internal.exception.DropDownloadPreparationException;
import dev.tuiop.filedrop.drop.internal.exception.DropIntegrityException;
import dev.tuiop.filedrop.drop.internal.exception.FileDropExpiredException;
import dev.tuiop.filedrop.drop.internal.exception.FileDropNotFoundException;
import dev.tuiop.filedrop.drop.internal.exception.FileDropNotEditableException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidMaxDownloadsException;
import dev.tuiop.filedrop.drop.internal.metadata.EncryptionMetadataMapper;
import dev.tuiop.filedrop.drop.internal.validation.DropRequestValidator;
import dev.tuiop.filedrop.integrity.ChecksumService;
import dev.tuiop.filedrop.scanning.FileValidator;
import dev.tuiop.filedrop.storage.ObjectStorage;
import dev.tuiop.filedrop.storage.TemporaryFileStorage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileDropService {

    private final FileDropRepository fileDropRepository;
    private final DropRequestValidator dropRequestValidator;
    private final FileValidator fileDropValidator;
    private final TemporaryFileStorage temporaryFileStorage;
    private final ChecksumService checksumService;
    private final FileEncryptionService fileEncryptionService;
    private final ObjectStorage objectStorage;
    private final EncryptionMetadataMapper encryptionMetadataMapper;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final FileDropProperties fileDropProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;


    public CreateDropResponse create(MultipartFile file, CreateDropRequest request) {
        fileDropValidator.firstFileValidation(file);

        dropRequestValidator.validate(request);

        String fileDropPassword = null;
        // hash password if it is present
        if (request.password() != null) {
            fileDropPassword = passwordService.hash(request.password());
        }

        Path tempFile = temporaryFileStorage.store(file);
        Throwable processingFailure = null;

        EncryptedFile encryptedFile = null;
        FileDrop persistedDrop = null;
        boolean uploadAttempted = false;
        String objectKey = null;
        try {
            long size = Files.size(tempFile);
            String contentType = fileDropValidator.preStoreFileValidation(tempFile);

            String sha256Checksum = checksumService.calculateSha256(tempFile);

            encryptedFile = fileEncryptionService.encrypt(tempFile);

            objectKey = UUID.randomUUID().toString();

            String downloadToken = tokenService.generateToken();
            String managementToken = tokenService.generateToken();
            String downloadTokenHash = tokenService.hashToken(downloadToken);
            String managementTokenHash = tokenService.hashToken(managementToken);


            FileDrop fileDrop = FileDrop.builder()
                    .encryptionMetadataEntity(encryptionMetadataMapper.toEntity(encryptedFile.encryptionMetadata()))
                    .originalFileName(file.getOriginalFilename())
                    .detectedContentType(contentType)
                    .size(size)
                    .storageKey(objectKey)
                    .sha256(sha256Checksum)
                    .downloadTokenHash(downloadTokenHash)
                    .managementTokenHash(managementTokenHash)
                    .passwordHash(fileDropPassword)
                    .expiresAt(request.expiresAt())
                    .maxDownloads(request.maxDownloads())
                    .status(FileDropStatus.PENDING)
                    .build();

            String downloadUrl = createDownloadUrl(downloadToken);

            persistedDrop = fileDropRepository.saveAndFlush(fileDrop);

            uploadAttempted = true;

            try (InputStream inputStream = Files.newInputStream(encryptedFile.path())) {
                objectStorage.store(objectKey, inputStream, encryptedFile.size());
            }

            persistedDrop.markAvailable();
            FileDrop availableDrop = fileDropRepository.saveAndFlush(persistedDrop);

            return CreateDropResponse.builder()
                    .id(availableDrop.getId())
                    .managementToken(managementToken)
                    .downloadUrl(downloadUrl)
                    .expiresAt(availableDrop.getExpiresAt())
                    .build();


        } catch (IOException exception) {
            DropCreationException dropCreationException = new DropCreationException(exception);
            processingFailure = dropCreationException;
            markCreationFailed(persistedDrop, dropCreationException);
            throw dropCreationException;
        } catch (RuntimeException | Error exception) {
            processingFailure = exception;
            markCreationFailed(persistedDrop, exception);
            throw exception;
        } finally {
            cleanupResources(
                    tempFile,
                    encryptedFile,
                    uploadAttempted,
                    objectKey,
                    processingFailure
            );
        }
    }

    public DropDownloadResult getFileDropAndMetadata(String token) {
        validateToken(token);
        String tokenHash = tokenService.hashToken(token);

        FileDrop candidate = findByTokenHash(tokenHash);
        validateDownload(candidate);

        Path stagedFile = stageAndVerify(candidate);

        try {
            FileDrop reservedDrop = reserveDownload(tokenHash);

            return new DropDownloadResult(
                    reservedDrop.getOriginalFileName(),
                    reservedDrop.getDetectedContentType(),
                    reservedDrop.getSize(),
                    reservedDrop.getDownloadsRemaining(),
                    createDownloadBody(stagedFile)
            );
        } catch (RuntimeException | Error exception) {
            deleteStagedFile(stagedFile, exception);
            throw exception;
        }
    }

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
        return toDetailsResponse(drop);
    }

    @Transactional
    public void requestDeletion(UUID id, String managementToken) {
        FileDrop drop = findByIdAndManagementTokenHashForUpdate(id, managementToken);

        if(drop.getStatus() == FileDropStatus.DELETED || drop.getStatus() == FileDropStatus.DELETION_PENDING){
            return;
        }
        drop.markDeletionPending();
        fileDropRepository.save(drop);


    }

    private FileDrop findByIdAndManagementTokenHashForUpdate(UUID id, String managementToken) {
        validateToken(managementToken);
        String managementTokenHash = tokenService.hashToken(managementToken);
        return fileDropRepository
                .findByIdAndManagementTokenHashForUpdate(id, managementTokenHash)
                .orElseThrow(FileDropNotFoundException::new);
    }

    private void ensureEditable(FileDrop drop) {
        if (drop.getStatus() != FileDropStatus.AVAILABLE) {
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

    private FileDrop reserveDownload(String tokenHash) {
        return transactionTemplate.execute(status -> {
            FileDrop drop = findByTokenHashAndLock(tokenHash);
            validateDownload(drop);
            registerDownload(drop);
            return drop;
        });
    }

    private void validateToken(String token) {
        if (token == null || !token.matches("^[A-Za-z0-9_-]{43}$")) {
            throw new FileDropNotFoundException();
        }
    }

    private FileDrop findByTokenHash(String tokenHash) {
        return fileDropRepository.findByDownloadTokenHash(tokenHash)
                .orElseThrow(FileDropNotFoundException::new);
    }

    private FileDrop findByTokenHashAndLock(String tokenHash) {
        return fileDropRepository.findByDownloadTokenHashForUpdate(tokenHash)
                .orElseThrow(FileDropNotFoundException::new);
    }

    private void validateDownload(FileDrop drop) {
        if (drop.getStatus() != FileDropStatus.AVAILABLE) {
            throw new FileDropNotFoundException();
        }
        if (drop.isExpired(clock.instant())) {
            throw new FileDropExpiredException();
        }
        if (drop.getDownloadCount() >= drop.getMaxDownloads()) {
            throw new DownloadLimitExceededException();
        }
    }

    private void registerDownload(FileDrop drop) {
        drop.increaseDownloadCount();
        if(drop.getDownloadsRemaining() == 0){
            drop.markDeletionPending();
        }
    }

    private Path stageAndVerify(FileDrop drop) {
        Path stagedFile = temporaryFileStorage.create();

        try {
            try (InputStream encrypted = objectStorage.load(drop.getStorageKey());
                 InputStream decrypted = fileEncryptionService.decrypt(
                         encrypted,
                         encryptionMetadataMapper.toRecord(
                                 drop.getEncryptionMetadataEntity()
                         )
                 );
                 OutputStream stagedOutput = Files.newOutputStream(stagedFile)) {
                decrypted.transferTo(stagedOutput);
            }

            verifyStagedFile(drop, stagedFile);
            return stagedFile;
        } catch (IOException exception) {
            DropDownloadPreparationException preparationException =
                    new DropDownloadPreparationException(exception);
            deleteStagedFile(stagedFile, preparationException);
            throw preparationException;
        } catch (RuntimeException | Error exception) {
            deleteStagedFile(stagedFile, exception);
            throw exception;
        }
    }

    private void verifyStagedFile(FileDrop drop, Path stagedFile)
            throws IOException {
        long actualSize = Files.size(stagedFile);
        if (actualSize != drop.getSize()) {
            throw new DropIntegrityException(
                    "Downloaded file size does not match the stored size."
            );
        }

        String actualChecksum = checksumService.calculateSha256(stagedFile);
        if (!actualChecksum.equalsIgnoreCase(drop.getSha256())) {
            throw new DropIntegrityException(
                    "Downloaded file checksum does not match the stored checksum."
            );
        }
    }

    private StreamingResponseBody createDownloadBody(Path stagedFile) {
        return outputStream -> {
            try (InputStream inputStream = Files.newInputStream(stagedFile)) {
                inputStream.transferTo(outputStream);
            } finally {
                deleteStagedFile(stagedFile, null);
            }
        };
    }

    private void deleteStagedFile(Path stagedFile, Throwable failure) {
        try {
            temporaryFileStorage.delete(stagedFile);
        } catch (RuntimeException cleanupFailure) {
            if (failure != null) {
                failure.addSuppressed(cleanupFailure);
                return;
            }

            log.error(
                    "Failed to delete staged download file '{}'.",
                    stagedFile,
                    cleanupFailure
            );
        }
    }

    private String createDownloadUrl(String downloadToken) {
        return fileDropProperties.baseUrl()
                .resolve("/api/v1/drops/d/" + downloadToken)
                .toString();
    }

    private void cleanupResources(
            Path plaintextFile,
            EncryptedFile encryptedFile,
            boolean uploadAttempted,
            String objectKey,
            Throwable processingFailure
    ) {
        RuntimeException cleanupFailure = null;

        cleanupFailure = attemptCleanup(
                () -> temporaryFileStorage.delete(plaintextFile),
                cleanupFailure
        );

        if (encryptedFile != null) {
            cleanupFailure = attemptCleanup(
                    () -> temporaryFileStorage.delete(encryptedFile.path()),
                    cleanupFailure
            );
        }

        if (uploadAttempted && processingFailure != null) {
            cleanupFailure = attemptCleanup(
                    () -> objectStorage.delete(objectKey),
                    cleanupFailure
            );
        }

        if (cleanupFailure == null) {
            return;
        }

        if (processingFailure != null) {
            processingFailure.addSuppressed(cleanupFailure);
            return;
        }

        throw cleanupFailure;
    }

    private void markCreationFailed(
            FileDrop persistedDrop,
            Throwable processingFailure
    ) {
        if (persistedDrop == null) {
            return;
        }

        try {
            persistedDrop.markFailed();
            fileDropRepository.saveAndFlush(persistedDrop);
        } catch (RuntimeException statusUpdateFailure) {
            processingFailure.addSuppressed(statusUpdateFailure);
        }
    }

    private RuntimeException attemptCleanup(
            Runnable cleanupOperation,
            RuntimeException previousFailure
    ) {
        try {
            cleanupOperation.run();
        } catch (RuntimeException currentFailure) {
            if (previousFailure == null) {
                return currentFailure;
            }

            previousFailure.addSuppressed(currentFailure);
        }

        return previousFailure;
    }


}
