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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileDropCleanupService {

    private final FileDropRepository repository;
    private final Integer CLEANUP_BATCH_SIZE;
    private final Duration PENDING_TIMEOUT;
    private final ObjectStorage objectStorage;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public FileDropCleanupService(FileDropRepository repository,
                                  @Value("${application.cleanup.batch-size}") Integer CLEANUP_BATCH_SIZE,
                                  @Value("${application.cleanup.pending-timeout}") Duration PENDING_TIMEOUT,
                                  ObjectStorage objectStorage,
                                  TransactionTemplate transactionTemplate,
                                  Clock clock) {
        this.repository = repository;
        this.CLEANUP_BATCH_SIZE = CLEANUP_BATCH_SIZE;
        this.PENDING_TIMEOUT = PENDING_TIMEOUT;
        this.objectStorage = objectStorage;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public void cleanup() {

        Instant now = clock.instant();
        Instant stalePendingBefore = now.minus(PENDING_TIMEOUT);

        List<UUID> ids = repository.findCleanupCandidatesIds(
                now,
                stalePendingBefore,
                Limit.of(CLEANUP_BATCH_SIZE)
        );

        for (var id : ids) {
            try{

                cleanupOne(id, now, stalePendingBefore);
            } catch (Exception e) {
                log.error("Failed to cleanup FileDrop {}", id, e);
            }

        }


    }

    private void cleanupOne(UUID id, Instant now, Instant stalePendingBefore) {
        String storageKey = transactionTemplate.execute(status ->
                {
                   FileDrop drop = repository.findById(id).orElse(null);

                   if(drop == null){
                       return null ;
                   }

                   if (!isCleanupCandidate(drop, now, stalePendingBefore)) {
                       return null;
                   }
                   drop.markDeletionPending();

                   return drop.getStorageKey();



                }
        );

        if(storageKey == null){
            return;
        }

        objectStorage.delete(storageKey);

        transactionTemplate.executeWithoutResult(transactionStatus ->
        {
            repository.findById(id).ifPresent(drop -> drop.markDeleted(clock.instant()));
        });



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
