package dev.tuiop.filedrop.drop.internal.validation;

import dev.tuiop.filedrop.access.PasswordService;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.exception.InvalidExpirationException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidMaxDownloadsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class DropRequestValidator {

    private final PasswordService passwordService;
    private final Duration minExpiration;
    private final Duration maxExpiration;
    private final Clock clock;

    public DropRequestValidator(
            PasswordService passwordService,
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

        this.passwordService = passwordService;
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
        passwordService.validate(request.password());
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

}
