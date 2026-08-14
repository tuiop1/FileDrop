package dev.tuiop.filedrop.access.internal;

import dev.tuiop.filedrop.access.InvalidPasswordException;
import dev.tuiop.filedrop.access.PasswordService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Argon2PasswordServiceTests {

    private final PasswordService passwordService = new Argon2PasswordService(
            new Argon2Password4jPasswordEncoder(),
            password -> new CompromisedPasswordDecision(false)
    );

    @Test
    void hashesAndMatchesUnicodePassword() {
        String password = "pässwörd-🔐";
        String hash = passwordService.hash(password);

        assertThat(hash).isNotEqualTo(password);
        assertThat(passwordService.matches(password, hash)).isTrue();
        assertThat(passwordService.matches("incorrect password", hash)).isFalse();
    }

    @Test
    void acceptsEightUnicodeCodePoints() {
        String password = "🔐".repeat(8);

        assertThatCode(() -> passwordService.validate(password)).doesNotThrowAnyException();
    }

    @Test
    void reportsFormatValidationErrors() {
        assertThatThrownBy(() -> passwordService.validate("short\n"))
                .isInstanceOf(InvalidPasswordException.class)
                .satisfies(exception -> {
                    InvalidPasswordException invalidPassword =
                            (InvalidPasswordException) exception;
                    assertThat(invalidPassword.errors().get("password"))
                            .containsExactly(
                                    "Password must be between 8 and 128 Unicode characters.",
                                    "Password must not contain control characters."
                            );
                });
    }

    @Test
    void rejectsCompromisedPassword() {
        PasswordService compromisedPasswordService = new Argon2PasswordService(
                new Argon2Password4jPasswordEncoder(),
                password -> new CompromisedPasswordDecision(true)
        );

        assertThatThrownBy(() -> compromisedPasswordService.validate("compromised password"))
                .isInstanceOf(InvalidPasswordException.class)
                .satisfies(exception -> {
                    InvalidPasswordException invalidPassword =
                            (InvalidPasswordException) exception;
                    assertThat(invalidPassword.errors().get("password"))
                            .containsExactly(
                                    "Password appears in a known data breach and must not be used."
                            );
                });
    }

    @Test
    void malformedPasswordNeverMatchesAStoredHash() {
        String hash = passwordService.hash("valid password");

        assertThat(passwordService.matches("short", hash)).isFalse();
        assertThat(passwordService.matches("password\n", hash)).isFalse();
        assertThat(passwordService.matches("x".repeat(129), hash)).isFalse();
    }
}
