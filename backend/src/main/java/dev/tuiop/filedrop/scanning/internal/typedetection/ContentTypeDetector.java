package dev.tuiop.filedrop.scanning.internal.typedetection;

import java.nio.file.Path;

public interface ContentTypeDetector {
    String detect(Path path);
}
