package dev.tuiop.filedrop.common.ratelimit;

import dev.tuiop.filedrop.common.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisFixedWindowRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rate-limit";
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisScript<Long> fixedWindowRateLimitScript;
    private final Clock clock;


    @Override
    public boolean allow(String scope, String clientId, long limit, Duration window) {
        long now = clock.millis();

        long windowMillis = window.toMillis();

        long windowIndex = now/windowMillis;


        long windowEnd = (windowIndex + 1) * windowMillis;

        long ttlMillis = windowEnd - now;

        String key = createKey(scope, clientId, windowIndex);

       Long hits = stringRedisTemplate.execute(
                fixedWindowRateLimitScript,
                List.of(key),
                String.valueOf(ttlMillis)
        );

        return hits != null && hits <= limit;


    }


    private String createKey(String scope, String clientId, long windowIndex){
        return "%s:%s:%s:%d".formatted(
                KEY_PREFIX,
                scope,
                clientId,
                windowIndex
        );

    }
}
