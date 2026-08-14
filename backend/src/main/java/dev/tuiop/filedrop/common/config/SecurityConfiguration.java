package dev.tuiop.filedrop.common.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.password.HaveIBeenPwnedRestApiPasswordChecker;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RequestMatcher createDrop = withDefaults()
                .matcher(HttpMethod.POST, "/api/v1/drops");
        RequestMatcher downloadDrop = withDefaults()
                .matcher(HttpMethod.GET, "/api/v1/drops/d/{token}");
        RequestMatcher downloadPasswordProtectedDrop = withDefaults()
                .matcher(HttpMethod.POST, "/api/v1/drops/d/{token}");
        RequestMatcher getDropDetails = withDefaults()
                .matcher(HttpMethod.GET, "/api/v1/drops/{id}");
        RequestMatcher updateDropExpiration = withDefaults()
                .matcher(HttpMethod.PATCH, "/api/v1/drops/{id}/expiration");
        RequestMatcher updateDropMaxDownloads = withDefaults()
                .matcher(HttpMethod.PATCH, "/api/v1/drops/{id}/max-downloads");
        RequestMatcher deleteDrop = withDefaults()
                .matcher(HttpMethod.DELETE, "/api/v1/drops/{id}");

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                createDrop,
                                downloadDrop,
                                downloadPasswordProtectedDrop,
                                getDropDetails,
                                updateDropExpiration,
                                updateDropMaxDownloads,
                                deleteDrop
                        ).permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }

    @Bean
    CompromisedPasswordChecker compromisedPasswordChecker() {
        return new HaveIBeenPwnedRestApiPasswordChecker();
    }
}
