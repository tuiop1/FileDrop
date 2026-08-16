package dev.tuiop.filedrop.storage.internal.temporary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TemporaryFileCleanupServiceTests {

    private static final Duration MAX_AGE = Duration.ofHours(1);

    @TempDir
    private Path tempDirectory;

    @Test
    void deletesStaleApplicationTempFiles() throws IOException {
        Path staleUpload = createFile("filedrop-upload", Instant.now().minus(Duration.ofHours(2)));
        Path staleDownload = createFile("filedrop-download-", Instant.now().minus(Duration.ofHours(2)));
        TemporaryFileCleanupService cleanupService = cleanupService();

        int deletedFiles = cleanupService.cleanup();

        assertThat(deletedFiles).isEqualTo(2);
        assertThat(staleUpload).doesNotExist();
        assertThat(staleDownload).doesNotExist();
    }

    @Test
    void keepsRecentAndUnrelatedFiles() throws IOException {
        Path recentFile = createFile("filedrop-recent-", Instant.now());
        Path unrelatedFile = createFile("another-application-", Instant.now().minus(Duration.ofHours(2)));
        Path nestedDirectory = Files.createDirectory(tempDirectory.resolve("filedrop-directory.tmp"));
        TemporaryFileCleanupService cleanupService = cleanupService();

        int deletedFiles = cleanupService.cleanup();

        assertThat(deletedFiles).isZero();
        assertThat(recentFile).exists();
        assertThat(unrelatedFile).exists();
        assertThat(nestedDirectory).exists();
    }

    @Test
    void doesNothingWhenTempDirectoryDoesNotExist() {
        TemporaryFileCleanupService cleanupService = new TemporaryFileCleanupService(
                tempDirectory.resolve("missing"),
                MAX_AGE
        );

        assertThat(cleanupService.cleanup()).isZero();
    }

    @Test
    void rejectsNonPositiveMaxAge() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TemporaryFileCleanupService(tempDirectory, Duration.ZERO))
                .withMessageContaining("max-age");
    }

    private TemporaryFileCleanupService cleanupService() {
        return new TemporaryFileCleanupService(tempDirectory, MAX_AGE);
    }

    private Path createFile(String prefix, Instant lastModified) throws IOException {
        Path file = Files.createTempFile(tempDirectory, prefix, ".tmp");
        Files.setLastModifiedTime(file, FileTime.from(lastModified));
        return file;
    }
}
