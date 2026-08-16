package dev.tuiop.filedrop.storage.internal.temporary;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TemporaryFileCleanupScheduler {

    private final TemporaryFileCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${application.cleanup.interval}")
    public void runCleanup() {
        cleanupService.cleanup();
    }
}
