package dev.tuiop.filedrop.drop.internal.validation;

import dev.tuiop.filedrop.access.PasswordService;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.exception.InvalidExpirationException;
import dev.tuiop.filedrop.drop.internal.exception.InvalidMaxDownloadsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DropRequestValidatorTests {

    private static final Instant NOW = Instant.parse("2026-08-14T12:00:00Z");
    private static final Duration MIN_EXPIRATION = Duration.ofMinutes(1);
    private static final Duration MAX_EXPIRATION = Duration.ofDays(7);

    private PasswordService passwordService;
    private DropRequestValidator validator;

    @BeforeEach
    void setUp() {
        passwordService = mock(PasswordService.class);
        validator = new DropRequestValidator(
                passwordService,
                MIN_EXPIRATION,
                MAX_EXPIRATION,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void acceptsExpirationAtBothConfiguredBoundaries() {
        assertThatCode(() -> validator.validateExpiration(NOW.plus(MIN_EXPIRATION)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validateExpiration(NOW.plus(MAX_EXPIRATION)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsExpirationOutsideConfiguredWindow() {
        assertThatThrownBy(() -> validator.validateExpiration(
                NOW.plus(MIN_EXPIRATION).minusNanos(1)
        )).isInstanceOf(InvalidExpirationException.class);

        assertThatThrownBy(() -> validator.validateExpiration(
                NOW.plus(MAX_EXPIRATION).plusNanos(1)
        )).isInstanceOf(InvalidExpirationException.class);
    }

    @Test
    void validatesMaximumDownloadBoundaries() {
        assertThatCode(() -> validator.validateMaxDownloads(1)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validateMaxDownloads(100)).doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateMaxDownloads(0))
                .isInstanceOf(InvalidMaxDownloadsException.class);
        assertThatThrownBy(() -> validator.validateMaxDownloads(101))
                .isInstanceOf(InvalidMaxDownloadsException.class);
        assertThatThrownBy(() -> validator.validateMaxDownloads(null))
                .isInstanceOf(InvalidMaxDownloadsException.class);
    }

    @Test
    void delegatesPasswordPolicyToPasswordService() {
        String password = "correct horse battery staple";
        CreateDropRequest request = new CreateDropRequest(
                10,
                NOW.plus(Duration.ofHours(1)),
                password
        );

        validator.validate(request);

        verify(passwordService).validate(password);
    }
}
