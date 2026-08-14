package dev.tuiop.filedrop.integrity;

import java.nio.file.Path;

public interface ChecksumService {

    String calculateSha256(Path path);
}
