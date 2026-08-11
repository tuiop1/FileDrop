package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.dto.CreateDropRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileDropService {

    private final FileDropRepository fileDropRepository;
    private final CreateDropRequestValidator createDropRequestValidator;



    public FileDrop create(MultipartFile file, CreateDropRequest request){
        createDropRequestValidator.validate(request);

        return new FileDrop();
    }
}
