package dev.tuiop.filedrop.common.ratelimit;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTests {

    private static final Validator VALIDATOR = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsPositiveLimitsAndWindows() {
        var properties = new RateLimitProperties(
                new RateLimitProperties.Policy(100, Duration.ofMinutes(1)),
                new RateLimitProperties.Policy(5, Duration.ofMinutes(1))
        );

        assertThat(VALIDATOR.validate(properties)).isEmpty();
    }

    @Test
    void rejectsMissingPoliciesAndNonPositiveValues() {
        var properties = new RateLimitProperties(
                new RateLimitProperties.Policy(0, Duration.ZERO),
                null
        );

        assertThat(VALIDATOR.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder(
                        "request.limit",
                        "request.windowValid",
                        "upload"
                );
    }
}
