package dev.tuiop.filedrop.drop.internal.cleanup;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileDropCleanupScheduler {


    private final FileDropCleanupService cleanupService;



    @Scheduled(fixedDelayString = "${application.cleanup.interval}")
    public void runCleanup(){
        cleanupService.cleanup();

    }
}
