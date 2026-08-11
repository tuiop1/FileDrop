package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.validation.CreateDropRequestValidator;
import dev.tuiop.filedrop.scanning.FileValidator;
import dev.tuiop.filedrop.storage.TemporaryFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class FileDropService {

    private final FileDropRepository fileDropRepository;
    private final CreateDropRequestValidator createDropRequestValidator;
    private final FileValidator fileDropValidator;
    private final TemporaryFileStorage temporaryFileStorage;


    public FileDrop create(MultipartFile file, CreateDropRequest request) {
        fileDropValidator.firstFileValidation(file);

        createDropRequestValidator.validate(request);


        Path tempFile = temporaryFileStorage.store(file);

        Throwable processingFailure = null;

        try {
            String contentType = fileDropValidator.preStoreFileValidation(tempFile);

            // Permanent storage and entity creation will be added here.
            return new FileDrop();
        } catch (RuntimeException | Error exception) {
            processingFailure = exception;
            throw exception;
        } finally {
            deleteTemporaryFile(tempFile, processingFailure);
        }
    }

    private void deleteTemporaryFile(Path tempFile, Throwable processingFailure) {
        try {
            temporaryFileStorage.delete(tempFile);
        } catch (RuntimeException cleanupException) {
            if (processingFailure != null) {
                processingFailure.addSuppressed(cleanupException);
                return;
            }

            throw cleanupException;
        }
    }
}
