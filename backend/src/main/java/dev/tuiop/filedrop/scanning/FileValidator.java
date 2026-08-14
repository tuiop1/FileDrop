package dev.tuiop.filedrop.scanning;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileValidator {

    void firstFileValidation(MultipartFile file);

    String preStoreFileValidation(Path path);
}
