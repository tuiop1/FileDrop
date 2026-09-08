package dev.tuiop.filedrop.storage.internal.temporary;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

@Service
@Slf4j
public class TemporaryFileCleanupService {

    private static final String TEMP_FILE_PREFIX = "filedrop-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    private final Path tempDirectory;
    private final Duration maxAge;

    public TemporaryFileCleanupService(
            @Value("${application.temporary-storage.directory}") Path tempDirectory,
            @Value("${application.temporary-storage.cleanup.max-age}") Duration maxAge
    ) {
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException(
                    "application.temporary-storage.cleanup.max-age must be greater than zero"
            );
        }

        this.tempDirectory = tempDirectory;
        this.maxAge = maxAge;
    }

    public int cleanup() {
        if (Files.notExists(tempDirectory)) {
            return 0;
        }

        Instant staleBefore = Instant.now().minus(maxAge);

        try (Stream<Path> files = Files.list(tempDirectory)) {
            int deletedCount = files
                    .filter(this::isApplicationTempFile)
                    .mapToInt(path -> deleteIfStale(path, staleBefore))
                    .sum();

            if (deletedCount > 0) {
                log.info(
                        "Temporary file cleanup completed deleted={}",
                        deletedCount
                );
            } else {
                log.debug("Temporary file cleanup completed with no stale files");
            }

            return deletedCount;
        } catch (IOException | SecurityException exception) {
            log.error("Failed to inspect temporary file directory", exception);
            return 0;
        }
    }

    private boolean isApplicationTempFile(Path path) {
        String fileName = path.getFileName().toString();

        return fileName.startsWith(TEMP_FILE_PREFIX)
                && fileName.endsWith(TEMP_FILE_SUFFIX)
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private int deleteIfStale(Path path, Instant staleBefore) {
        try {
            Instant lastModified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
            if (lastModified.isAfter(staleBefore)) {
                return 0;
            }

            if (Files.deleteIfExists(path)) {
                log.debug("Deleted stale temporary file fileName={}", path.getFileName());
                return 1;
            }
        } catch (IOException | SecurityException exception) {
            log.warn(
                    "Failed to delete stale temporary file fileName={}",
                    path.getFileName(),
                    exception
            );
        }

        return 0;
    }
}
