package dev.tuiop.filedrop.storage.internal.object;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class S3ConfigurationTests {

    private final S3Configuration configuration = new S3Configuration();

    @Test
    void createsAmazonS3Client() {
        S3Properties properties = new S3Properties(
                null,
                "eu-central-1",
                "filedrop",
                null,
                null,
                false
        );

        try (S3Client client = configuration.amazonS3Client(properties)) {
            assertNotNull(client);
        }
    }

    @Test
    void createsMinioS3Client() {
        S3Properties properties = new S3Properties(
                "http://localhost:9000",
                "us-east-1",
                "filedrop",
                "access-key",
                "secret-key",
                true
        );

        try (S3Client client = configuration.minioS3Client(properties)) {
            assertNotNull(client);
        }
    }
}
