package dev.tuiop.filedrop.storage.internal.object;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "application.storage.s3")
public record S3Properties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyle
) {}
