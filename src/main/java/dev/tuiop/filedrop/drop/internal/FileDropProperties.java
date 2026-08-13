package dev.tuiop.filedrop.drop.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "application")
public record FileDropProperties(
        URI baseUrl
) {
}
