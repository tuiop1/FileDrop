package dev.tuiop.filedrop.crypto.internal;

import dev.tuiop.filedrop.crypto.EncryptedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AesGcmFileEncryptionServiceTests {

    @TempDir
    Path tempDirectory;

    @Test
    void encryptsAndDecryptsFileWithoutChangingItsContent() throws Exception {
        byte[] originalContent = "confidential file contents 🔐"
                .getBytes(StandardCharsets.UTF_8);
        Path plaintextFile = Files.write(tempDirectory.resolve("plain.txt"), originalContent);
        AesGcmFileEncryptionService encryptionService =
                new AesGcmFileEncryptionService(masterKeyProvider());

        EncryptedFile encryptedFile = encryptionService.encrypt(plaintextFile);

        assertThat(Files.readAllBytes(encryptedFile.path())).isNotEqualTo(originalContent);
        assertThat(encryptedFile.encryptionMetadata().keyVersion()).isEqualTo(1);

        try (InputStream encryptedInput = Files.newInputStream(encryptedFile.path());
             InputStream decryptedInput = encryptionService.decrypt(
                     encryptedInput,
                     encryptedFile.encryptionMetadata()
             )) {
            assertThat(decryptedInput.readAllBytes()).isEqualTo(originalContent);
        }
    }

    private MasterKeyProvider masterKeyProvider() {
        SecretKey key = new SecretKeySpec(new byte[32], "AES");

        return new MasterKeyProvider() {
            @Override
            public SecretKey getKey() {
                return key;
            }

            @Override
            public int getVersion() {
                return 1;
            }
        };
    }
}
