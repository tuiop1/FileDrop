package dev.tuiop.filedrop.crypto;

import java.io.InputStream;
import java.nio.file.Path;

public interface FileEncryptionService {
    EncryptedFile encrypt(Path plaintextFile);

    InputStream decrypt(InputStream encryptedFile,
                        EncryptionMetadata metadata);
}
