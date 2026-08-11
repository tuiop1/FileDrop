package dev.tuiop.filedrop.storage.internal.temporary;

import dev.tuiop.filedrop.storage.TemporaryFileStorage;
import dev.tuiop.filedrop.storage.internal.temporary.exception.TemporaryFileStorageException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TemporaryFileStorageImpl implements TemporaryFileStorage {

    private final Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"), "filedrop");



    public Path store (MultipartFile file){
        try{
            Files.createDirectories(tempDirectory);

            Path tempFile = Files.createTempFile(
                    tempDirectory,
                    "filedrop-",
                    ".tmp"
            );
            file.transferTo(tempFile);

            return tempFile;
        } catch (IOException e) {
            throw new TemporaryFileStorageException("Failed to store the uploaded file temporarily.", e);
        }

    }

    public void delete(Path path){
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new TemporaryFileStorageException("Failed to delete the temporary file.", e);
        }
    }
}
