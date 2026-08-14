package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.access.PasswordService;
import dev.tuiop.filedrop.access.TokenService;
import dev.tuiop.filedrop.crypto.FileEncryptionService;
import dev.tuiop.filedrop.drop.internal.exception.DownloadPasswordRequiredException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidDownloadPasswordException;
import dev.tuiop.filedrop.drop.internal.metadata.EncryptionMetadataMapper;
import dev.tuiop.filedrop.drop.internal.validation.DropRequestValidator;
import dev.tuiop.filedrop.integrity.ChecksumService;
import dev.tuiop.filedrop.scanning.FileValidator;
import dev.tuiop.filedrop.storage.ObjectStorage;
import dev.tuiop.filedrop.storage.TemporaryFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDropServicePasswordTests {

    private static final String TOKEN = "a".repeat(43);
    private static final String TOKEN_HASH = "download-token-hash";
    private static final String PASSWORD_HASH = "password-hash";
    private static final String VALID_PASSWORD = "correct horse battery staple";
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");

    @Mock
    private FileDropRepository fileDropRepository;
    @Mock
    private DropRequestValidator dropRequestValidator;
    @Mock
    private FileValidator fileDropValidator;
    @Mock
    private TemporaryFileStorage temporaryFileStorage;
    @Mock
    private ChecksumService checksumService;
    @Mock
    private FileEncryptionService fileEncryptionService;
    @Mock
    private ObjectStorage objectStorage;
    @Mock
    private EncryptionMetadataMapper encryptionMetadataMapper;
    @Mock
    private PasswordService passwordService;
    @Mock
    private TokenService tokenService;
    @Mock
    private FileDropProperties fileDropProperties;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private Clock clock;

    @InjectMocks
    private FileDropService fileDropService;

    @BeforeEach
    void setUp() {
        when(tokenService.hashToken(TOKEN)).thenReturn(TOKEN_HASH);
        when(clock.instant()).thenReturn(NOW);
        when(fileDropRepository.findByDownloadTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(protectedDrop()));
    }

    @Test
    void protectedDropRequiresPasswordBeforeStagingOrReservation() {
        assertThatThrownBy(() -> fileDropService.getFileDropAndMetadata(TOKEN))
                .isInstanceOf(DownloadPasswordRequiredException.class)
                .satisfies(exception -> {
                    DownloadPasswordRequiredException required =
                            (DownloadPasswordRequiredException) exception;
                    assertThat(required.code()).isEqualTo("DOWNLOAD_PASSWORD_REQUIRED");
                    assertThat(required.status()).isEqualTo(401);
                });

        verify(passwordService, never()).matches(anyString(), anyString());
        verify(fileDropRepository, never()).findByDownloadTokenHashForUpdate(TOKEN_HASH);
        verifyNoInteractions(temporaryFileStorage, objectStorage);
    }

    @Test
    void incorrectPasswordIsRejectedBeforeStagingOrReservation() {
        when(passwordService.matches("incorrect password", PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(
                () -> fileDropService.getFileDropAndMetadata(TOKEN, "incorrect password")
        )
                .isInstanceOf(InvalidDownloadPasswordException.class)
                .satisfies(exception -> {
                    InvalidDownloadPasswordException invalid =
                            (InvalidDownloadPasswordException) exception;
                    assertThat(invalid.code()).isEqualTo("INVALID_DOWNLOAD_PASSWORD");
                    assertThat(invalid.status()).isEqualTo(401);
                });

        verify(passwordService).matches("incorrect password", PASSWORD_HASH);
        verify(fileDropRepository, never()).findByDownloadTokenHashForUpdate(TOKEN_HASH);
        verifyNoInteractions(temporaryFileStorage, objectStorage);
    }

    @Test
    void correctPasswordAllowsDownloadPreparationToBegin() {
        RuntimeException stagingFailure = new RuntimeException("staging reached");
        when(passwordService.matches(VALID_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(temporaryFileStorage.create()).thenThrow(stagingFailure);

        assertThatThrownBy(
                () -> fileDropService.getFileDropAndMetadata(TOKEN, VALID_PASSWORD)
        ).isSameAs(stagingFailure);

        verify(passwordService).matches(VALID_PASSWORD, PASSWORD_HASH);
        verify(temporaryFileStorage).create();
        verify(fileDropRepository, never()).findByDownloadTokenHashForUpdate(TOKEN_HASH);
    }

    @Test
    void unprotectedDropStillUsesTheExistingGetDownloadFlow() {
        RuntimeException stagingFailure = new RuntimeException("staging reached");
        FileDrop unprotectedDrop = unprotectedDrop();
        when(fileDropRepository.findByDownloadTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(unprotectedDrop));
        when(temporaryFileStorage.create()).thenThrow(stagingFailure);

        assertThatThrownBy(
                () -> fileDropService.getFileDropAndMetadata(TOKEN)
        ).isSameAs(stagingFailure);

        verifyNoInteractions(passwordService);
        verify(temporaryFileStorage).create();
        verify(fileDropRepository, never()).findByDownloadTokenHashForUpdate(TOKEN_HASH);
    }

    private FileDrop protectedDrop() {
        return FileDrop.builder()
                .status(FileDropStatus.AVAILABLE)
                .expiresAt(NOW.plusSeconds(3_600))
                .maxDownloads(5)
                .downloadCount(0)
                .passwordHash(PASSWORD_HASH)
                .build();
    }

    private FileDrop unprotectedDrop() {
        return FileDrop.builder()
                .status(FileDropStatus.AVAILABLE)
                .expiresAt(NOW.plusSeconds(3_600))
                .maxDownloads(5)
                .downloadCount(0)
                .build();
    }
}
