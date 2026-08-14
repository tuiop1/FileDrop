package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.access.PasswordService;
import dev.tuiop.filedrop.access.TokenService;
import dev.tuiop.filedrop.crypto.FileEncryptionService;
import dev.tuiop.filedrop.drop.internal.dto.UpdateExpirationRequest;
import dev.tuiop.filedrop.drop.internal.dto.UpdateMaxDownloadsRequest;
import dev.tuiop.filedrop.drop.internal.exception.FileDropNotEditableException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidMaxDownloadsException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDropServiceManagementTests {

    private static final UUID DROP_ID = UUID.fromString("d4a3708b-34e2-42df-97b2-6288e253bb4c");
    private static final String MANAGEMENT_TOKEN = "m".repeat(43);
    private static final String MANAGEMENT_TOKEN_HASH = "management-token-hash";
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

    private FileDrop expiredDrop;

    @BeforeEach
    void setUp() {
        expiredDrop = FileDrop.builder()
                .status(FileDropStatus.AVAILABLE)
                .expiresAt(NOW.minusSeconds(1))
                .maxDownloads(10)
                .downloadCount(0)
                .build();

        when(tokenService.hashToken(MANAGEMENT_TOKEN)).thenReturn(MANAGEMENT_TOKEN_HASH);
    }

    @Test
    void expiredAvailableDropCannotBeRevivedByChangingExpiration() {
        Instant originalExpiration = expiredDrop.getExpiresAt();
        useManagedDrop(expiredDrop);
        when(clock.instant()).thenReturn(NOW);

        assertThatThrownBy(() -> fileDropService.updateExpiration(
                DROP_ID,
                MANAGEMENT_TOKEN,
                new UpdateExpirationRequest(NOW.plusSeconds(3_600))
        ))
                .isInstanceOf(FileDropNotEditableException.class)
                .satisfies(exception -> {
                    FileDropNotEditableException notEditable =
                            (FileDropNotEditableException) exception;
                    assertThat(notEditable.code()).isEqualTo("FILE_DROP_NOT_EDITABLE");
                    assertThat(notEditable.status()).isEqualTo(409);
                });

        assertThat(expiredDrop.getExpiresAt()).isEqualTo(originalExpiration);
        verifyNoInteractions(dropRequestValidator);
    }

    @Test
    void expiredAvailableDropCannotChangeMaximumDownloads() {
        useManagedDrop(expiredDrop);
        when(clock.instant()).thenReturn(NOW);

        assertThatThrownBy(() -> fileDropService.updateMaxDownloads(
                DROP_ID,
                MANAGEMENT_TOKEN,
                new UpdateMaxDownloadsRequest(20)
        )).isInstanceOf(FileDropNotEditableException.class);

        assertThat(expiredDrop.getMaxDownloads()).isEqualTo(10);
        verifyNoInteractions(dropRequestValidator);
    }

    @Test
    void changesExpirationForActiveDrop() {
        FileDrop activeDrop = activeDrop(2, 10);
        Instant newExpiration = NOW.plusSeconds(7_200);
        useManagedDrop(activeDrop);
        when(clock.instant()).thenReturn(NOW);

        var details = fileDropService.updateExpiration(
                DROP_ID,
                MANAGEMENT_TOKEN,
                new UpdateExpirationRequest(newExpiration)
        );

        assertThat(activeDrop.getExpiresAt()).isEqualTo(newExpiration);
        assertThat(details.expiresAt()).isEqualTo(newExpiration);
        verify(dropRequestValidator).validateExpiration(newExpiration);
    }

    @Test
    void changesMaximumDownloadsWhenItExceedsCurrentCount() {
        FileDrop activeDrop = activeDrop(3, 10);
        useManagedDrop(activeDrop);
        when(clock.instant()).thenReturn(NOW);

        var details = fileDropService.updateMaxDownloads(
                DROP_ID,
                MANAGEMENT_TOKEN,
                new UpdateMaxDownloadsRequest(5)
        );

        assertThat(activeDrop.getMaxDownloads()).isEqualTo(5);
        assertThat(details.maxDownloads()).isEqualTo(5);
        assertThat(details.downloadsRemaining()).isEqualTo(2);
        verify(dropRequestValidator).validateMaxDownloads(5);
    }

    @Test
    void maximumDownloadsMustExceedCurrentDownloadCount() {
        FileDrop activeDrop = activeDrop(3, 10);
        useManagedDrop(activeDrop);
        when(clock.instant()).thenReturn(NOW);

        assertThatThrownBy(() -> fileDropService.updateMaxDownloads(
                DROP_ID,
                MANAGEMENT_TOKEN,
                new UpdateMaxDownloadsRequest(3)
        ))
                .isInstanceOf(InvalidMaxDownloadsException.class)
                .hasMessageContaining("current download count of 3");

        assertThat(activeDrop.getMaxDownloads()).isEqualTo(10);
        verify(dropRequestValidator).validateMaxDownloads(3);
    }

    @Test
    void deletionMarksDropPendingAndPersistsIt() {
        FileDrop activeDrop = activeDrop(0, 10);
        useManagedDrop(activeDrop);

        fileDropService.requestDeletion(DROP_ID, MANAGEMENT_TOKEN);

        assertThat(activeDrop.getStatus()).isEqualTo(FileDropStatus.DELETION_PENDING);
        verify(fileDropRepository).save(activeDrop);
    }

    @Test
    void repeatedDeletionRequestIsIdempotent() {
        FileDrop deletionPendingDrop = activeDrop(0, 10);
        deletionPendingDrop.markDeletionPending();
        useManagedDrop(deletionPendingDrop);

        fileDropService.requestDeletion(DROP_ID, MANAGEMENT_TOKEN);

        assertThat(deletionPendingDrop.getStatus()).isEqualTo(FileDropStatus.DELETION_PENDING);
        verify(fileDropRepository, never()).save(deletionPendingDrop);
    }

    private void useManagedDrop(FileDrop drop) {
        when(fileDropRepository.findByIdAndManagementTokenHashForUpdate(
                DROP_ID,
                MANAGEMENT_TOKEN_HASH
        )).thenReturn(Optional.of(drop));
    }

    private FileDrop activeDrop(int downloadCount, int maxDownloads) {
        return FileDrop.builder()
                .id(DROP_ID)
                .originalFileName("report.txt")
                .detectedContentType("text/plain")
                .size(128L)
                .createdAt(NOW.minusSeconds(3_600))
                .expiresAt(NOW.plusSeconds(3_600))
                .maxDownloads(maxDownloads)
                .downloadCount(downloadCount)
                .status(FileDropStatus.AVAILABLE)
                .build();
    }
}
