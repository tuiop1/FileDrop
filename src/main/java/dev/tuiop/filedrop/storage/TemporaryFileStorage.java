package dev.tuiop.filedrop.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface TemporaryFileStorage {


    Path store(MultipartFile file);

    void delete(Path path);
}
