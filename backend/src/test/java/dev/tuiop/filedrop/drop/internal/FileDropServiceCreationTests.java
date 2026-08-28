package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.access.PasswordService;
import dev.tuiop.filedrop.access.TokenService;
import dev.tuiop.filedrop.crypto.EncryptedFile;
import dev.tuiop.filedrop.crypto.EncryptionMetadata;
import dev.tuiop.filedrop.crypto.FileEncryptionService;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDropServiceCreationTests {

    private static final Instant EXPIRATION = Instant.parse("2026-08-15T12:00:00Z");
    private static final String DOWNLOAD_TOKEN = "d".repeat(43);
    private static final String MANAGEMENT_TOKEN = "m".repeat(43);
    private static final String DOWNLOAD_TOKEN_HASH = "download-token-hash";
    private static final String MANAGEMENT_TOKEN_HASH = "management-token-hash";
    private static final String CHECKSUM = "sha-256-checksum";
    private static final String PASSWORD = "correct horse battery staple";
    private static final String PASSWORD_HASH = "password-hash";

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
    @InjectMocks
    private FileDropService fileDropService;

    private MockMultipartFile upload;
    private CreateDropRequest request;
    private Path plaintextFile;
    private Path encryptedPath;
    private EncryptedFile encryptedFile;
    private EncryptionMetadataEntity metadataEntity;
    private FileDrop persistedDrop;

    @BeforeEach
    void setUp() throws Exception {
        byte[] content = "important test content".getBytes();
        upload = new MockMultipartFile("file", "report.txt", "text/plain", content);
        request = new CreateDropRequest(10, EXPIRATION, PASSWORD);

        plaintextFile = Files.write(tempDirectory.resolve("upload.tmp"), content);
        encryptedPath = Files.write(tempDirectory.resolve("encrypted.bin"), new byte[]{1, 2, 3});
        EncryptionMetadata metadata = new EncryptionMetadata(
                new byte[]{4},
                new byte[]{5},
                new byte[]{6},
                1
        );
        encryptedFile = new EncryptedFile(encryptedPath, Files.size(encryptedPath), metadata);
        metadataEntity = EncryptionMetadataEntity.builder()
                .encryptedDataKey(new byte[]{4})
                .fileIv(new byte[]{5})
                .keyIv(new byte[]{6})
                .keyVersion(1)
                .build();
        persistedDrop = FileDrop.builder()
                .id(UUID.fromString("ef3a23b9-faa3-4a7b-ad60-02a0e370ec50"))
                .expiresAt(EXPIRATION)
                .status(FileDropStatus.PENDING)
                .build();

        when(temporaryFileStorage.store(upload)).thenReturn(plaintextFile);
        when(fileDropValidator.validateStagedFile(plaintextFile)).thenReturn("text/plain");
        when(checksumService.calculateSha256(plaintextFile)).thenReturn(CHECKSUM);
        when(fileEncryptionService.encrypt(plaintextFile)).thenReturn(encryptedFile);
        when(encryptionMetadataMapper.toEntity(metadata)).thenReturn(metadataEntity);
        when(tokenService.generateToken()).thenReturn(DOWNLOAD_TOKEN, MANAGEMENT_TOKEN);
        when(tokenService.hashToken(DOWNLOAD_TOKEN)).thenReturn(DOWNLOAD_TOKEN_HASH);
        when(tokenService.hashToken(MANAGEMENT_TOKEN)).thenReturn(MANAGEMENT_TOKEN_HASH);
        when(passwordService.hash(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(fileDropProperties.baseUrl()).thenReturn(URI.create("http://localhost:8080"));
        when(fileDropRepository.saveAndFlush(any(FileDrop.class))).thenReturn(persistedDrop);
    }

    @Test
    void createsAvailableDropAndCleansTemporaryFiles() throws Exception {
        // Arrange
        long plaintextFileSize = Files.size(plaintextFile);

        // Act
        CreateDropResponse response = fileDropService.create(upload, request);

        // Assert
        assertThat(response.id()).isEqualTo(persistedDrop.getId());
        assertThat(response.managementToken()).isEqualTo(MANAGEMENT_TOKEN);
        assertThat(response.downloadUrl())
                .isEqualTo("http://localhost:8080/api/v1/drops/d/" + DOWNLOAD_TOKEN);
        assertThat(response.expiresAt()).isEqualTo(EXPIRATION);
        assertThat(persistedDrop.getStatus()).isEqualTo(FileDropStatus.AVAILABLE);

        ArgumentCaptor<FileDrop> dropCaptor = ArgumentCaptor.forClass(FileDrop.class);
        verify(fileDropRepository, times(2)).saveAndFlush(dropCaptor.capture());
        FileDrop newDrop = dropCaptor.getAllValues().getFirst();
        assertThat(newDrop.getOriginalFileName()).isEqualTo("report.txt");
        assertThat(newDrop.getDetectedContentType()).isEqualTo("text/plain");
        assertThat(newDrop.getSize()).isEqualTo(plaintextFileSize);
        assertThat(newDrop.getSha256()).isEqualTo(CHECKSUM);
        assertThat(newDrop.getDownloadTokenHash()).isEqualTo(DOWNLOAD_TOKEN_HASH);
        assertThat(newDrop.getManagementTokenHash()).isEqualTo(MANAGEMENT_TOKEN_HASH);
        assertThat(newDrop.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(newDrop.getStatus()).isEqualTo(FileDropStatus.PENDING);

        verify(fileDropValidator).validateUpload(upload);
        verify(dropRequestValidator).validate(request);
        verify(objectStorage).store(anyString(), any(InputStream.class), eq(encryptedFile.size()));
        verify(temporaryFileStorage).delete(plaintextFile);
        verify(temporaryFileStorage).delete(encryptedPath);
        verify(objectStorage, never()).delete(anyString());
    }

    @Test
    void temporaryFileCleanupFailureDoesNotFailSuccessfulCreation() {
        // Arrange
        RuntimeException cleanupFailure = new RuntimeException("temporary storage unavailable");
        doThrow(cleanupFailure).when(temporaryFileStorage).delete(plaintextFile);

        // Act
        CreateDropResponse response = fileDropService.create(upload, request);

        // Assert
        assertThat(response.id()).isEqualTo(persistedDrop.getId());
        assertThat(persistedDrop.getStatus()).isEqualTo(FileDropStatus.AVAILABLE);
        verify(temporaryFileStorage).delete(plaintextFile);
        verify(temporaryFileStorage).delete(encryptedPath);
        verify(objectStorage, never()).delete(anyString());
    }

    @Test
    void storageFailureMarksDropFailedAndRemovesPartialObject() {
        // Arrange
        RuntimeException storageFailure = new RuntimeException("storage unavailable");
        doThrow(storageFailure)
                .when(objectStorage)
                .store(anyString(), any(InputStream.class), eq(encryptedFile.size()));

        // Act
        Throwable thrown = catchThrowable(() -> fileDropService.create(upload, request));

        // Assert
        assertThat(thrown).isSameAs(storageFailure);
        assertThat(persistedDrop.getStatus()).isEqualTo(FileDropStatus.FAILED);
        verify(fileDropRepository, times(2)).saveAndFlush(any(FileDrop.class));

        ArgumentCaptor<String> objectKey = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).delete(objectKey.capture());
        assertThat(objectKey.getValue()).isNotBlank();
        verify(temporaryFileStorage).delete(plaintextFile);
        verify(temporaryFileStorage).delete(encryptedPath);
    }
}
