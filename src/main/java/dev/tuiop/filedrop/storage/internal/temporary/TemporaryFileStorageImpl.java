package dev.tuiop.filedrop.storage.internal.temporary;

import dev.tuiop.filedrop.storage.TemporaryFileStorage;
import dev.tuiop.filedrop.storage.internal.temporary.exception.TemporaryFileStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TemporaryFileStorageImpl implements TemporaryFileStorage {

    private final Path tempDirectory;

    public TemporaryFileStorageImpl(
            @Value("${application.filedrop.temporary-storage.directory}") Path tempDirectory
    ) {
        this.tempDirectory = tempDirectory;
    }

    public Path store(MultipartFile file) {
        long expectedSize = file.getSize();
        Path tempFile = null;

        try {
            Files.createDirectories(tempDirectory);

            tempFile = Files.createTempFile(
                    tempDirectory,
                    "filedrop-",
                    ".tmp"
            );

            file.transferTo(tempFile);

            long actualSize = Files.size(tempFile);
            if (expectedSize != actualSize) {
                throw new IOException(
                        "Transferred file size mismatch: expected %d bytes but stored %d bytes"
                                .formatted(expectedSize, actualSize)
                );
            }

            return tempFile;
        } catch (IOException | RuntimeException e) {
            TemporaryFileStorageException storageException = new TemporaryFileStorageException(
                    "Failed to store the uploaded file temporarily.",
                    e
            );

            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupException) {
                    storageException.addSuppressed(cleanupException);
                }
            }

            throw storageException;
        }
    }

    @Override
    public Path create() {
        try {
            Files.createDirectories(tempDirectory);
            return Files.createTempFile(
                    tempDirectory,
                    "filedrop-download-",
                    ".tmp"
            );
        } catch (IOException | RuntimeException exception) {
            throw new TemporaryFileStorageException(
                    "Failed to create a temporary download file.",
                    exception
            );
        }
    }

    @Override
    public void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new TemporaryFileStorageException("Failed to delete the temporary file.", e);
        }
    }
}
