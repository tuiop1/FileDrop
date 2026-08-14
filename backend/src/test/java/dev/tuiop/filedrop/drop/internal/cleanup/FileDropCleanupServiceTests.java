package dev.tuiop.filedrop.drop.internal.cleanup;

import dev.tuiop.filedrop.drop.internal.FileDrop;
import dev.tuiop.filedrop.drop.internal.FileDropRepository;
import dev.tuiop.filedrop.drop.internal.FileDropStatus;
import dev.tuiop.filedrop.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDropCleanupServiceTests {

    private static final int BATCH_SIZE = 25;
    private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final Instant DELETED_AT = NOW.plusSeconds(2);
    private static final UUID DROP_ID = UUID.fromString("cd4ca733-51fd-4629-931c-fe62d24eaa9b");

    @Mock
    private FileDropRepository repository;
    @Mock
    private ObjectStorage objectStorage;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private Clock clock;

    private FileDropCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new FileDropCleanupService(
                repository,
                BATCH_SIZE,
                PENDING_TIMEOUT,
                objectStorage,
                transactionTemplate,
                clock
        );
    }

    @Test
    void deletesExpiredObjectAndMarksDropDeleted() {
        FileDrop drop = expiredDrop();
        stubCandidateQuery();
        when(repository.findById(DROP_ID)).thenReturn(Optional.of(drop));
        when(clock.instant()).thenReturn(NOW, DELETED_AT);
        executeFirstTransactionImmediately();
        executeFinalTransactionImmediately();

        cleanupService.cleanup();

        verify(objectStorage).delete("stored-object-key");
        assertThat(drop.getStatus()).isEqualTo(FileDropStatus.DELETED);
        assertThat(drop.getDeletedAt()).isEqualTo(DELETED_AT);
    }

    @Test
    void storageFailureLeavesDropPendingForALaterRetry() {
        FileDrop drop = expiredDrop();
        stubCandidateQuery();
        when(repository.findById(DROP_ID)).thenReturn(Optional.of(drop));
        when(clock.instant()).thenReturn(NOW);
        executeFirstTransactionImmediately();
        doThrow(new RuntimeException("object storage unavailable"))
                .when(objectStorage).delete("stored-object-key");

        cleanupService.cleanup();

        assertThat(drop.getStatus()).isEqualTo(FileDropStatus.DELETION_PENDING);
        assertThat(drop.getDeletedAt()).isNull();
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    private void stubCandidateQuery() {
        when(repository.findCleanupCandidatesIds(
                NOW,
                NOW.minus(PENDING_TIMEOUT),
                Limit.of(BATCH_SIZE)
        )).thenReturn(List.of(DROP_ID));
    }

    private void executeFirstTransactionImmediately() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private void executeFinalTransactionImmediately() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private FileDrop expiredDrop() {
        return FileDrop.builder()
                .id(DROP_ID)
                .storageKey("stored-object-key")
                .status(FileDropStatus.AVAILABLE)
                .createdAt(NOW.minus(Duration.ofHours(1)))
                .expiresAt(NOW.minusSeconds(1))
                .maxDownloads(10)
                .downloadCount(0)
                .build();
    }
}
