package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.validation.CreateDropRequestValidator;
import dev.tuiop.filedrop.drop.internal.validation.FileDropValidator;
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
    private final FileDropValidator fileDropValidator;
    private final TemporaryFileStorage temporaryFileStorage;



    public FileDrop create(MultipartFile file, CreateDropRequest request){
        fileDropValidator.firstFileDropValidation(file);

        createDropRequestValidator.validate(request);


        Path tempFile = temporaryFileStorage.store(file);





        return new FileDrop();
    }
}
