package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.access.PasswordService;
import dev.tuiop.filedrop.access.TokenService;
import dev.tuiop.filedrop.crypto.EncryptedFile;
import dev.tuiop.filedrop.crypto.FileEncryptionService;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropResponse;
import dev.tuiop.filedrop.drop.internal.dto.DropDownloadResult;
import dev.tuiop.filedrop.drop.internal.exception.DownloadLimitExceededException;
import dev.tuiop.filedrop.drop.internal.exception.DropCreationException;
import dev.tuiop.filedrop.drop.internal.exception.FileDropExpiredException;
import dev.tuiop.filedrop.drop.internal.exception.FileDropNotFoundException;
import dev.tuiop.filedrop.drop.internal.validation.CreateDropRequestValidator;
import dev.tuiop.filedrop.integrity.ChecksumService;
import dev.tuiop.filedrop.scanning.FileValidator;
import dev.tuiop.filedrop.storage.ObjectStorage;
import dev.tuiop.filedrop.storage.TemporaryFileStorage;
import lombok.RequiredArgsConstructor;
import org.hibernate.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileDropService {

    private final FileDropRepository fileDropRepository;
    private final CreateDropRequestValidator createDropRequestValidator;
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


    public CreateDropResponse create(MultipartFile file, CreateDropRequest request) {
        fileDropValidator.firstFileValidation(file);

        createDropRequestValidator.validate(request);

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
                    .size(encryptedFile.size())
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

    public DropDownloadResult getFileDropAndMetadata(String token){

        FileDrop drop = prepareDownload(token);








    }


    private FileDrop prepareDownload(String token){
        return transactionTemplate.execute(status -> {

            validateToken(token);
            FileDrop drop = findByTokenAndLock(token);
            validateDownload(drop);
            drop.increaseDownloadCount();
            fileDropRepository.save(drop);
            return drop;
        });

    }

    private void validateToken(String token) {
        if (token == null || !token.matches("^[A-Za-z0-9_-]{43}$")) {
            throw new FileDropNotFoundException();
        }
    }

    private FileDrop findByTokenAndLock(String token) {
        String tokenHash = tokenService.hashToken(token);

        return fileDropRepository.findByDownloadTokenHash(tokenHash)
                .orElseThrow(FileDropNotFoundException::new);
    }

    private void validateDownload(FileDrop drop) {
        if (drop.getDownloadCount() >= drop.getMaxDownloads()) {
            throw new DownloadLimitExceededException();
        }
        if (drop.isExpired()) {
            throw new FileDropExpiredException();
        }
    }




    private String createDownloadUrl(String downloadToken) {
        return fileDropProperties.baseUrl()
                .resolve("/d/" + downloadToken)
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
