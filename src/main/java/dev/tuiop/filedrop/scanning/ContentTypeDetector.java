package dev.tuiop.filedrop.scanning;

import java.nio.file.Path;

public interface ContentTypeDetector {

    String detect(Path path);
}
