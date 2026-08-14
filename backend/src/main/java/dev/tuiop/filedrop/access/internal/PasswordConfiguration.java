package dev.tuiop.filedrop.access.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder;

@Configuration
class PasswordConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2Password4jPasswordEncoder();
    }
}