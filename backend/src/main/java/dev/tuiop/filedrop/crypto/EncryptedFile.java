package dev.tuiop.filedrop.crypto;

import java.nio.file.Path;

public record EncryptedFile(
        Path path,
        long size,
        EncryptionMetadata encryptionMetadata
) {
}
