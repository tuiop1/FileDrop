package dev.tuiop.filedrop.storage.internal.object;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
class S3Configuration {

    @Bean
    S3Client s3Client(S3Properties properties) {

        return S3Client.builder()
                .endpointOverride(
                        URI.create(properties.endpoint())
                )
                .region(
                        Region.of(properties.region())
                )
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        properties.accessKey(),
                                        properties.secretKey()
                                )
                        )
                )
                .forcePathStyle(properties.pathStyle())
                .build();
    }
}