package dev.tuiop.filedrop.common;

import java.time.Duration;

public interface RateLimiter {

    boolean allow(
            String scope,
            String clientId,
            long limit,
            Duration window
    );

}
