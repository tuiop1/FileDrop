package dev.tuiop.filedrop.access.internal;

import dev.tuiop.filedrop.access.InvalidPasswordException;
import dev.tuiop.filedrop.access.PasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class Argon2PasswordService implements PasswordService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final PasswordEncoder passwordEncoder;
    private final CompromisedPasswordChecker compromisedPasswordChecker;

    @Override
    public void validate(String rawPassword) {
        if (rawPassword == null) {
            return;
        }

        List<String> errors = formatValidationErrors(rawPassword);

        if (!rawPassword.isBlank()
                && compromisedPasswordChecker.check(rawPassword).isCompromised()) {
            errors.add("Password appears in a known data breach and must not be used.");
        }

        if (!errors.isEmpty()) {
            throw new InvalidPasswordException(errors);
        }
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null
                && passwordHash != null
                && formatValidationErrors(rawPassword).isEmpty()
                && passwordEncoder.matches(rawPassword, passwordHash);
    }

    private List<String> formatValidationErrors(String rawPassword) {
        List<String> errors = new ArrayList<>();

        if (rawPassword.isBlank()) {
            errors.add("Password must not be blank.");
        }

        int passwordLength = rawPassword.codePointCount(0, rawPassword.length());

        if (passwordLength < MIN_PASSWORD_LENGTH || passwordLength > MAX_PASSWORD_LENGTH) {
            errors.add(
                    "Password must be between %d and %d Unicode characters."
                            .formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH)
            );
        }

        if (rawPassword.codePoints().anyMatch(Character::isISOControl)) {
            errors.add("Password must not contain control characters.");
        }

        return errors;
    }
}
