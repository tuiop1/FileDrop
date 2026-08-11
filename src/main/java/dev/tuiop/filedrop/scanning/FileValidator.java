package dev.tuiop.filedrop.scanning;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileValidator {

    void firstFileDropValidation(MultipartFile file);

    String preStoreFileDropValidation(Path path);
}
