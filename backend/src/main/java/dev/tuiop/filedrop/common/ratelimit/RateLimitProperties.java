package dev.tuiop.filedrop.common.ratelimit;


import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "application.rate-limit")
public record RateLimitProperties(
        @Valid @NotNull Policy request,
        @Valid @NotNull Policy upload
) {

    public record Policy(
            @Positive int limit,
            @NotNull Duration window
    ) {

        @AssertTrue(message = "window must be at least 1ms")
        public boolean isWindowValid() {
            return window == null || window.compareTo(Duration.ofMillis(1)) >= 0;
        }
    }
}
