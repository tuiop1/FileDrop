package dev.tuiop.filedrop.drop.internal.cleanup;


import dev.tuiop.filedrop.drop.internal.FileDrop;
import dev.tuiop.filedrop.drop.internal.FileDropRepository;
import dev.tuiop.filedrop.drop.internal.FileDropStatus;
import dev.tuiop.filedrop.storage.ObjectStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileDropCleanupService {

    private final FileDropRepository repository;
    private final int cleanupBatchSize;
    private final Duration pendingTimeout;
    private final ObjectStorage objectStorage;
    private final TransactionTemplate transactionTemplate;

    public FileDropCleanupService(FileDropRepository repository,
                                  @Value("${application.cleanup.batch-size}") int cleanupBatchSize,
                                  @Value("${application.cleanup.pending-timeout}") Duration pendingTimeout,
                                  ObjectStorage objectStorage,
                                  TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.cleanupBatchSize = cleanupBatchSize;
        this.pendingTimeout = pendingTimeout;
        this.objectStorage = objectStorage;
        this.transactionTemplate = transactionTemplate;
    }

    public void cleanup() {
        Instant now = Instant.now();
        Instant stalePendingBefore = now.minus(pendingTimeout);

        List<UUID> ids = repository.findCleanupCandidatesIds(
                now,
                stalePendingBefore,
                Limit.of(cleanupBatchSize)
        );

        if (ids.isEmpty()) {
            log.debug("File drop cleanup completed with no candidates");
            return;
        }

        int cleanedCount = 0;
        int failureCount = 0;

        for (var id : ids) {
            try {
                if (cleanupOne(id, now, stalePendingBefore)) {
                    cleanedCount++;
                }
            } catch (Exception e) {
                failureCount++;
                log.error("Failed to clean up file drop dropId={}", id, e);
            }
        }

        log.info(
                "File drop cleanup completed candidates={} deleted={} failures={}",
                ids.size(),
                cleanedCount,
                failureCount
        );
    }

    private boolean cleanupOne(UUID id, Instant now, Instant stalePendingBefore) {
        String storageKey = transactionTemplate.execute(status -> {
            FileDrop drop = repository.findById(id).orElse(null);

            if (drop == null || !isCleanupCandidate(drop, now, stalePendingBefore)) {
                return null;
            }

            drop.markDeletionPending();
            return drop.getStorageKey();
        });

        if (storageKey == null) {
            return false;
        }

        objectStorage.delete(storageKey);

        transactionTemplate.executeWithoutResult(transactionStatus -> {
            repository.findById(id).ifPresent(drop -> drop.markDeleted(Instant.now()));
        });

        return true;
    }

    private boolean isCleanupCandidate(
            FileDrop drop,
            Instant now,
            Instant stalePendingBefore
    ) {
        return switch (drop.getStatus()) {
            case DELETION_PENDING, FAILED -> true;
            case PENDING -> !drop.getCreatedAt().isAfter(stalePendingBefore);
            case AVAILABLE -> !drop.getExpiresAt().isAfter(now)
                    || drop.getDownloadCount() >= drop.getMaxDownloads();
            case DELETED -> false;
        };
    }


}
