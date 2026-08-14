package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.access.PasswordService;
import dev.tuiop.filedrop.access.TokenService;
import dev.tuiop.filedrop.crypto.EncryptionMetadata;
import dev.tuiop.filedrop.crypto.FileEncryptionService;
import dev.tuiop.filedrop.drop.internal.dto.DropDownloadResult;
import dev.tuiop.filedrop.drop.internal.exception.DownloadLimitExceededException;
import dev.tuiop.filedrop.drop.internal.exception.DropIntegrityException;
import dev.tuiop.filedrop.drop.internal.exception.FileDropExpiredException;
import dev.tuiop.filedrop.drop.internal.metadata.EncryptionMetadataEntity;
import dev.tuiop.filedrop.drop.internal.metadata.EncryptionMetadataMapper;
import dev.tuiop.filedrop.drop.internal.validation.DropRequestValidator;
import dev.tuiop.filedrop.integrity.ChecksumService;
import dev.tuiop.filedrop.scanning.FileValidator;
import dev.tuiop.filedrop.storage.ObjectStorage;
import dev.tuiop.filedrop.storage.TemporaryFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDropServiceDownloadTests {

    private static final String TOKEN = "d".repeat(43);
    private static final String TOKEN_HASH = "download-token-hash";
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final byte[] FILE_CONTENT = "downloaded content".getBytes();
    private static final String CHECKSUM = "expected-checksum";

    @TempDir
    Path tempDirectory;

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
    }

    @Test
    void expiredDropIsRejectedBeforeFileStaging() {
        when(fileDropRepository.findByDownloadTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(drop(NOW.minusSeconds(1), 0, 2)));

        assertThatThrownBy(() -> fileDropService.getFileDropAndMetadata(TOKEN))
                .isInstanceOf(FileDropExpiredException.class);

        verifyNoInteractions(temporaryFileStorage, objectStorage);
        verify(fileDropRepository, never()).findByDownloadTokenHashForUpdate(TOKEN_HASH);
    }

    @Test
    void exhaustedDropIsRejectedBeforeFileStaging() {
        when(fileDropRepository.findByDownloadTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(drop(NOW.plusSeconds(60), 2, 2)));

        assertThatThrownBy(() -> fileDropService.getFileDropAndMetadata(TOKEN))
                .isInstanceOf(DownloadLimitExceededException.class);

        verifyNoInteractions(temporaryFileStorage, objectStorage);
        verify(fileDropRepository, never()).findByDownloadTokenHashForUpdate(TOKEN_HASH);
    }

    @Test
    void lastDownloadIsReservedAndMarksDropForDeletion() throws Exception {
        FileDrop drop = drop(NOW.plusSeconds(60), 0, 1);
        Path stagedFile = prepareSuccessfulStaging(drop);
        when(fileDropRepository.findByDownloadTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(drop));
        when(fileDropRepository.findByDownloadTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(drop));
        executeTransactionsImmediately();

        DropDownloadResult result = fileDropService.getFileDropAndMetadata(TOKEN);

        assertThat(result.filename()).isEqualTo("report.txt");
        assertThat(result.downloadsRemaining()).isZero();
        assertThat(drop.getDownloadCount()).isEqualTo(1);
        assertThat(drop.getStatus()).isEqualTo(FileDropStatus.DELETION_PENDING);
        verify(temporaryFileStorage, never()).delete(stagedFile);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.body().writeTo(output);

        assertThat(output.toByteArray()).isEqualTo(FILE_CONTENT);
        verify(temporaryFileStorage).delete(stagedFile);
    }

    @Test
    void reservationRechecksTheLimitAfterStagingAndCleansStagedFile() {
        FileDrop candidate = drop(NOW.plusSeconds(60), 0, 1);
        FileDrop concurrentlyExhausted = drop(NOW.plusSeconds(60), 1, 1);
        Path stagedFile = prepareSuccessfulStaging(candidate);
        when(fileDropRepository.findByDownloadTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(candidate));
        when(fileDropRepository.findByDownloadTokenHashForUpdate(TOKEN_HASH))
                .thenReturn(Optional.of(concurrentlyExhausted));
        executeTransactionsImmediately();

        assertThatThrownBy(() -> fileDropService.getFileDropAndMetadata(TOKEN))
                .isInstanceOf(DownloadLimitExceededException.class);

        verify(temporaryFileStorage).delete(stagedFile);
        assertThat(candidate.getDownloadCount()).isZero();
    }

    @Test
    void integrityFailureDoesNotConsumeAReservationAndCleansStagedFile() {
        FileDrop drop = drop(NOW.plusSeconds(60), 0, 2);
        Path stagedFile = prepareSuccessfulStaging(drop);
        when(checksumService.calculateSha256(stagedFile)).thenReturn("wrong-checksum");
        when(fileDropRepository.findByDownloadTokenHash(TOKEN_HASH))
                .thenReturn(Optional.of(drop));

        assertThatThrownBy(() -> fileDropService.getFileDropAndMetadata(TOKEN))
                .isInstanceOf(DropIntegrityException.class);

        verify(temporaryFileStorage).delete(stagedFile);
        verify(fileDropRepository, never()).findByDownloadTokenHashForUpdate(TOKEN_HASH);
        assertThat(drop.getDownloadCount()).isZero();
        assertThat(drop.getDownloadsRemaining()).isEqualTo(2);
    }

    private Path prepareSuccessfulStaging(FileDrop drop) {
        Path stagedFile = tempDirectory.resolve("staged-download.tmp");
        EncryptionMetadata metadata = new EncryptionMetadata(
                new byte[]{1},
                new byte[]{2},
                new byte[]{3},
                1
        );

        when(temporaryFileStorage.create()).thenReturn(stagedFile);
        when(objectStorage.load(drop.getStorageKey()))
                .thenReturn(new ByteArrayInputStream(new byte[]{9, 8, 7}));
        when(encryptionMetadataMapper.toRecord(drop.getEncryptionMetadataEntity()))
                .thenReturn(metadata);
        when(fileEncryptionService.decrypt(any(ByteArrayInputStream.class), any(EncryptionMetadata.class)))
                .thenReturn(new ByteArrayInputStream(FILE_CONTENT));
        when(checksumService.calculateSha256(stagedFile)).thenReturn(CHECKSUM);
        return stagedFile;
    }

    private void executeTransactionsImmediately() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private FileDrop drop(Instant expiresAt, int downloadCount, int maxDownloads) {
        return FileDrop.builder()
                .originalFileName("report.txt")
                .detectedContentType("text/plain")
                .size((long) FILE_CONTENT.length)
                .storageKey("object-key")
                .sha256(CHECKSUM)
                .encryptionMetadataEntity(EncryptionMetadataEntity.builder().build())
                .status(FileDropStatus.AVAILABLE)
                .expiresAt(expiresAt)
                .maxDownloads(maxDownloads)
                .downloadCount(downloadCount)
                .build();
    }
}
