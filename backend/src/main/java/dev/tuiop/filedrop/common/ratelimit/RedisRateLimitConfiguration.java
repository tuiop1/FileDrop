package dev.tuiop.filedrop.common.ratelimit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisRateLimitConfiguration {

    @Bean
    RedisScript<Long> fixedWindowRateLimitScript(){
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        script.setLocation(new ClassPathResource(
                "redis/fixed-window-rate-limiter.lua"
        ));

        script.setResultType(Long.class);
        return script;
    }

}
