package dev.tuiop.filedrop.storage;

import java.io.InputStream;

public interface ObjectStorage {

    void store(
            String objectKey,
            InputStream content,
            long contentLength
    );

    InputStream load(String objectKey);

    void delete(String objectKey);
}
