package dev.tuiop.filedrop.crypto.internal;

import dev.tuiop.filedrop.crypto.EncryptedFile;
import dev.tuiop.filedrop.crypto.EncryptionMetadata;
import dev.tuiop.filedrop.crypto.FileEncryptionService;
import dev.tuiop.filedrop.crypto.MasterKeyProvider;
import dev.tuiop.filedrop.crypto.internal.exception.FileEncryptionException;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class AesGcmFileEncryptionService implements FileEncryptionService {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE_BITS = 256;
    private static final int GCM_TAG_SIZE_BITS = 128;
    private static final int IV_SIZE_BYTES = 12;
    private static final int BUFFER_SIZE_BYTES = 8192;

    private final MasterKeyProvider masterKeyProvider;
    private final SecureRandom secureRandom;

    public AesGcmFileEncryptionService(MasterKeyProvider masterKeyProvider) {
        this.masterKeyProvider = masterKeyProvider;
        this.secureRandom = new SecureRandom();
    }

    @Override
    public EncryptedFile encrypt(Path plaintextFile) {
        Path encryptedFile = null;

        try {
            SecretKey masterKey = masterKeyProvider.getKey();
            int keyVersion = masterKeyProvider.getVersion();
            SecretKey dataKey = generateDataKey();
            byte[] fileIv = generateIv();
            byte[] keyIv = generateIv();

            encryptedFile = createEncryptedTemporaryFile(plaintextFile);
            encryptFile(plaintextFile, encryptedFile, dataKey, fileIv);

            byte[] encryptedDataKey = encryptDataKey(
                    dataKey,
                    masterKey,
                    keyIv
            );

            EncryptionMetadata metadata = new EncryptionMetadata(
                    encryptedDataKey,
                    fileIv,
                    keyIv,
                    keyVersion
            );

            return new EncryptedFile(
                    encryptedFile,
                    Files.size(encryptedFile),
                    metadata
            );
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            FileEncryptionException encryptionException = new FileEncryptionException(
                    "Failed to encrypt file.",
                    exception
            );

            deletePartialEncryptedFile(encryptedFile, encryptionException);
            throw encryptionException;
        }
    }

    private SecretKey generateDataKey() throws GeneralSecurityException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(AES_KEY_SIZE_BITS, secureRandom);
        return generator.generateKey();
    }

    private byte[] generateIv() {
        byte[] iv = new byte[IV_SIZE_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private Path createEncryptedTemporaryFile(Path plaintextFile) throws IOException {
        Path parentDirectory = plaintextFile.toAbsolutePath().getParent();

        if (parentDirectory == null) {
            throw new IOException("Plaintext file has no parent directory.");
        }

        return Files.createTempFile(
                parentDirectory,
                "filedrop-encrypted-",
                ".bin"
        );
    }

    private void encryptFile(
            Path plaintextFile,
            Path encryptedFile,
            SecretKey dataKey,
            byte[] fileIv
    ) throws IOException, GeneralSecurityException {
        Cipher cipher = createEncryptionCipher(dataKey, fileIv);

        try (InputStream inputStream = Files.newInputStream(plaintextFile);
             OutputStream outputStream = Files.newOutputStream(encryptedFile)) {

            byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] encryptedChunk = cipher.update(buffer, 0, bytesRead);

                if (encryptedChunk != null && encryptedChunk.length > 0) {
                    outputStream.write(encryptedChunk);
                }
            }

            outputStream.write(cipher.doFinal());
        }
    }

    private byte[] encryptDataKey(
            SecretKey dataKey,
            SecretKey masterKey,
            byte[] keyIv
    ) throws GeneralSecurityException {
        Cipher cipher = createEncryptionCipher(masterKey, keyIv);
        byte[] rawDataKey = dataKey.getEncoded();

        try {
            return cipher.doFinal(rawDataKey);
        } finally {
            Arrays.fill(rawDataKey, (byte) 0);
        }
    }

    private Cipher createEncryptionCipher(SecretKey key, byte[] iv)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(GCM_TAG_SIZE_BITS, iv),
                secureRandom
        );
        return cipher;
    }

    private void deletePartialEncryptedFile(
            Path encryptedFile,
            FileEncryptionException encryptionException
    ) {
        if (encryptedFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(encryptedFile);
        } catch (IOException cleanupException) {
            encryptionException.addSuppressed(cleanupException);
        }
    }

    @Override
    public InputStream decrypt(InputStream encryptedFile, EncryptionMetadata metadata) {
        return null;
    }
}
