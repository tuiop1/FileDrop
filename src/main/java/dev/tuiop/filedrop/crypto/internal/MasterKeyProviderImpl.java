package dev.tuiop.filedrop.crypto.internal;

import dev.tuiop.filedrop.crypto.MasterKeyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;

@Component
public final class MasterKeyProviderImpl implements MasterKeyProvider {

    private static final int AES_256_KEY_SIZE_BYTES = 32;

    private final SecretKey masterKey;
    private final int version;

    public MasterKeyProviderImpl(
            @Value("${application.encryption.master-key}") String encodedMasterKey,
            @Value("${application.encryption.key-version}") int version
    ) {
        if (version < 1) {
            throw new IllegalStateException("Encryption master-key version must be at least 1.");
        }

        if (encodedMasterKey == null || encodedMasterKey.isBlank()) {
            throw new IllegalStateException(
                    "Encryption master key is missing. Set FILEDROP_MASTER_KEY to a Base64-encoded 32-byte key."
            );
        }

        byte[] decodedKey;

        try {
            decodedKey = Base64.getDecoder().decode(encodedMasterKey.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Encryption master key must be valid Base64.",
                    exception
            );
        }

        try {
            if (decodedKey.length != AES_256_KEY_SIZE_BYTES) {
                throw new IllegalStateException(
                        "Encryption master key must decode to exactly 32 bytes for AES-256."
                );
            }

            this.masterKey = new SecretKeySpec(decodedKey, "AES");
            this.version = version;
        } finally {
            Arrays.fill(decodedKey, (byte) 0);
        }
    }

    @Override
    public SecretKey getKey() {
        return masterKey;
    }

    @Override
    public int getVersion() {
        return version;
    }
}
