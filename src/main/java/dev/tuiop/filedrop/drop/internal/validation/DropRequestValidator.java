package dev.tuiop.filedrop.drop.internal.validation;

import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.exception.InvalidExpirationException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidMaxDownloadsException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidPasswordException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class DropRequestValidator {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final CompromisedPasswordChecker compromisedPasswordChecker;
    private final Duration minExpiration;
    private final Duration maxExpiration;
    private final Clock clock;

    public DropRequestValidator(
            CompromisedPasswordChecker compromisedPasswordChecker,
            @Value("${application.file.expiration.min}") Duration minExpiration,
            @Value("${application.file.expiration.max}") Duration maxExpiration,
            Clock clock
    ) {
        if (minExpiration.isNegative() || minExpiration.isZero()) {
            throw new IllegalArgumentException("Minimum expiration duration must be positive.");
        }

        if (maxExpiration.compareTo(minExpiration) < 0) {
            throw new IllegalArgumentException(
                    "Maximum expiration duration must not be shorter than the minimum expiration duration."
            );
        }

        this.compromisedPasswordChecker = compromisedPasswordChecker;
        this.minExpiration = minExpiration;
        this.maxExpiration = maxExpiration;
        this.clock = clock;
    }

    public void validate(CreateDropRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create drop request must not be null.");
        }

        validateMaxDownloads(request.maxDownloads());

        validateExpiration(request.expiresAt());
        validatePassword(request.password());
    }

    public void validateMaxDownloads(Integer maxDownloads) {
        if (maxDownloads == null || maxDownloads < 1 || maxDownloads > 100) {
            throw new InvalidMaxDownloadsException(
                    "Maximum downloads must be between 1 and 100."
            );
        }
    }

    public void validateExpiration(Instant expiresAt) {
        if (expiresAt == null) {
            throw new InvalidExpirationException("Expiration time must not be null.");
        }

        Instant now = clock.instant();
        Instant earliestExpiration = now.plus(minExpiration);
        Instant latestExpiration = now.plus(maxExpiration);

        if (expiresAt.isBefore(earliestExpiration) || expiresAt.isAfter(latestExpiration)) {
            throw new InvalidExpirationException(
                    "Expiration time must be between %s and %s from now."
                            .formatted(minExpiration, maxExpiration)
            );
        }
    }

    private void validatePassword(String password) {
        if (password == null) {
            return;
        }

        List<String> errors = new ArrayList<>();

        if (password.isBlank()) {
            errors.add("Password must not be blank.");
        }

        int passwordLength = password.codePointCount(0, password.length());

        if (passwordLength < MIN_PASSWORD_LENGTH || passwordLength > MAX_PASSWORD_LENGTH) {
            errors.add(
                    "Password must be between %d and %d Unicode characters."
                            .formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH)
            );
        }

        if (password.codePoints().anyMatch(Character::isISOControl)) {
            errors.add("Password must not contain control characters.");
        }

        if (!password.isBlank() && compromisedPasswordChecker.check(password).isCompromised()) {
            errors.add(
                    "Password appears in a known data breach and must not be used."
            );
        }

        if (!errors.isEmpty()) {
            throw new InvalidPasswordException(errors);
        }
    }
}
