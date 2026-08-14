package dev.tuiop.filedrop.crypto;

public record EncryptionMetadata(
        byte[] encryptedDataKey,
        byte[] fileIv,
        byte[] keyIv,
        int keyVersion
) {
}
