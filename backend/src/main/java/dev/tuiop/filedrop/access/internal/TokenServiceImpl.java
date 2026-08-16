package dev.tuiop.filedrop.access.internal;

import dev.tuiop.filedrop.access.TokenService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenServiceImpl implements TokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();


    @Override
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    @Override
    public String hashToken(String token) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    e
            );
        }
    }

    @Override
    public boolean isValidFormat(String token) {
        if (token == null) {
            return false;
        }

        try {
            byte[] decodedToken = Base64.getUrlDecoder().decode(token);
            String canonicalToken = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(decodedToken);

            return decodedToken.length == TOKEN_BYTE_LENGTH
                    && canonicalToken.equals(token);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
