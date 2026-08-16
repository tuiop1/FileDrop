package dev.tuiop.filedrop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@ActiveProfiles("test")
class FileDropApplicationTests {

    @MockitoBean
    S3Client s3Client;

    @Test
    void contextLoads() {
    }

}
