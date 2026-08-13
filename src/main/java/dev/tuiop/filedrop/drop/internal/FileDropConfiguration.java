package dev.tuiop.filedrop.drop.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(FileDropProperties.class)
class FileDropConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
