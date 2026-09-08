package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.access.TokenService;
import dev.tuiop.filedrop.common.RateLimiter;
import dev.tuiop.filedrop.drop.internal.metadata.EncryptionMetadataEntity;
import dev.tuiop.filedrop.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:filedrop-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.task.scheduling.enabled=false",
        "application.base-url=http://localhost:8080",
        "application.encryption.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "application.storage.s3.region=us-east-1",
        "application.storage.s3.bucket=filedrop-test",
        "clamav.host=localhost",
        "clamav.port=3310"
})
@AutoConfigureMockMvc
class FileDropApplicationIntegrationTests {

    private static final String MANAGEMENT_TOKEN = "m".repeat(43);
    private static final String MANAGEMENT_TOKEN_HASH = "management-token-hash";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileDropRepository repository;

    @MockitoBean
    private RateLimiter rateLimiter;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private ObjectStorage objectStorage;

    private FileDrop drop;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        drop = repository.saveAndFlush(availableDrop());

        when(rateLimiter.allow(
                eq("request"),
                eq("127.0.0.1"),
                anyLong(),
                eq(Duration.ofMinutes(1))
        )).thenReturn(true);
        when(tokenService.isValidFormat(MANAGEMENT_TOKEN)).thenReturn(true);
        when(tokenService.hashToken(MANAGEMENT_TOKEN)).thenReturn(MANAGEMENT_TOKEN_HASH);
    }

    @Test
    void managementLifecycleFlowsThroughHttpServiceAndDatabase() throws Exception {
        String detailsPath = "/api/v1/drops/" + drop.getId();

        mockMvc.perform(get("/api/v1/drops/{id}", drop.getId())
                        .servletPath(detailsPath)
                        .header("X-Management-Token", MANAGEMENT_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(drop.getId().toString()))
                .andExpect(jsonPath("$.originalFileName").value("integration.txt"))
                .andExpect(jsonPath("$.downloadsRemaining").value(3))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.passwordProtected").value(true));

        mockMvc.perform(patch("/api/v1/drops/{id}/max-downloads", drop.getId())
                        .servletPath(detailsPath + "/max-downloads")
                        .header("X-Management-Token", MANAGEMENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxDownloads\": 7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDownloads").value(7))
                .andExpect(jsonPath("$.downloadsRemaining").value(7));

        assertThat(repository.findById(drop.getId()))
                .get()
                .extracting(FileDrop::getMaxDownloads)
                .isEqualTo(7);

        Instant updatedExpiration = Instant.now()
                .plus(Duration.ofDays(2))
                .truncatedTo(ChronoUnit.MILLIS);
        mockMvc.perform(patch("/api/v1/drops/{id}/expiration", drop.getId())
                        .servletPath(detailsPath + "/expiration")
                        .header("X-Management-Token", MANAGEMENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresAt\": \"%s\"}".formatted(updatedExpiration)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(updatedExpiration.toString()));

        assertThat(repository.findById(drop.getId()))
                .get()
                .extracting(FileDrop::getExpiresAt)
                .isEqualTo(updatedExpiration);

        mockMvc.perform(delete("/api/v1/drops/{id}", drop.getId())
                        .servletPath(detailsPath)
                        .header("X-Management-Token", MANAGEMENT_TOKEN))
                .andExpect(status().isNoContent());

        assertThat(repository.findById(drop.getId()))
                .get()
                .extracting(FileDrop::getStatus)
                .isEqualTo(FileDropStatus.DELETION_PENDING);
    }

    @Test
    void malformedManagementTokenIsPresentedAsNotFound() throws Exception {
        String detailsPath = "/api/v1/drops/" + drop.getId();

        mockMvc.perform(get("/api/v1/drops/{id}", drop.getId())
                        .servletPath(detailsPath)
                        .header("X-Management-Token", "malformed"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_DROP_NOT_FOUND"))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/drops/" + drop.getId()));
    }

    @Test
    void rateLimitFilterCanRejectBeforeTheController() throws Exception {
        when(rateLimiter.allow(
                eq("request"),
                eq("127.0.0.1"),
                anyLong(),
                eq(Duration.ofMinutes(1))
        )).thenReturn(false);

        mockMvc.perform(get("/api/v1/drops/{id}", drop.getId())
                        .servletPath("/api/v1/drops/" + drop.getId())
                        .header("X-Management-Token", MANAGEMENT_TOKEN))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Too many requests"));
    }

    private FileDrop availableDrop() {
        return FileDrop.builder()
                .encryptionMetadataEntity(EncryptionMetadataEntity.builder()
                        .encryptedDataKey(new byte[]{1, 2, 3})
                        .fileIv(new byte[]{4, 5, 6})
                        .keyIv(new byte[]{7, 8, 9})
                        .keyVersion(1)
                        .build())
                .originalFileName("integration.txt")
                .detectedContentType("text/plain")
                .size(20L)
                .storageKey("integration-storage-key")
                .sha256("b".repeat(64))
                .downloadTokenHash("download-token-hash")
                .managementTokenHash(MANAGEMENT_TOKEN_HASH)
                .passwordHash("password-hash")
                .expiresAt(Instant.now().plus(Duration.ofDays(1)))
                .maxDownloads(3)
                .status(FileDropStatus.AVAILABLE)
                .build();
    }
}
