package dev.tuiop.filedrop.common.config;

import dev.tuiop.filedrop.access.InvalidPasswordException;
import dev.tuiop.filedrop.common.RateLimiter;
import dev.tuiop.filedrop.common.internal.ApiExceptionHandler;
import dev.tuiop.filedrop.common.ratelimit.RateLimitFilter;
import dev.tuiop.filedrop.common.ratelimit.RateLimitProperties;
import dev.tuiop.filedrop.drop.internal.FileDropController;
import dev.tuiop.filedrop.drop.internal.FileDropManagementService;
import dev.tuiop.filedrop.drop.internal.FileDropService;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropResponse;
import dev.tuiop.filedrop.drop.internal.exception.DropCreationException;
import dev.tuiop.filedrop.drop.internal.exception.FileDropNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileDropController.class)
@Import({
        ApiExceptionHandler.class,
        RateLimitFilter.class,
        SecurityConfiguration.class
})
class FileDropWebMvcTests {

    private static final UUID DROP_ID =
            UUID.fromString("9fdb6f63-a487-4f17-a84b-77b1c62e74c3");
    private static final Instant EXPIRES_AT = Instant.parse("2100-01-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileDropService fileDropService;

    @MockitoBean
    private FileDropManagementService fileDropManagementService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @MockitoBean
    private RateLimitProperties rateLimitProperties;

    @BeforeEach
    void allowRequests() {
        var policy = new RateLimitProperties.Policy(100, Duration.ofMinutes(1));
        when(rateLimitProperties.request()).thenReturn(policy);
        when(rateLimitProperties.upload()).thenReturn(policy);
        when(rateLimiter.allow(anyString(), anyString(), anyLong(), any(Duration.class)))
                .thenReturn(true);
    }

    @Test
    void createsDropFromValidatedMultipartRequest() throws Exception {
        when(fileDropService.create(any(), any())).thenReturn(
                new CreateDropResponse(
                        DROP_ID,
                        "https://files.example/api/v1/drops/d/download-token",
                        "management-token",
                        EXPIRES_AT
                )
        );

        mockMvc.perform(multipart("/api/v1/drops")
                        .file(filePart())
                        .file(metadataPart(10, EXPIRES_AT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(DROP_ID.toString()))
                .andExpect(jsonPath("$.downloadUrl")
                        .value("https://files.example/api/v1/drops/d/download-token"))
                .andExpect(jsonPath("$.managementToken").value("management-token"))
                .andExpect(jsonPath("$.expiresAt").value(EXPIRES_AT.toString()));
    }

    @Test
    void beanValidationFailureUsesValidationApiErrorContract() throws Exception {
        mockMvc.perform(multipart("/api/v1/drops")
                        .file(filePart())
                        .file(metadataPart(0, EXPIRES_AT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed."))
                .andExpect(jsonPath("$.path").value("/api/v1/drops"))
                .andExpect(jsonPath("$.errors.maxDownloads[0]")
                        .value("must be greater than or equal to 1"));

        verifyNoInteractions(fileDropService);
    }

    @Test
    void domainValidationFailurePreservesFieldErrors() throws Exception {
        when(fileDropService.create(any(), any())).thenThrow(
                new InvalidPasswordException(List.of(
                        "Password must contain at least eight characters.",
                        "Password must not contain control characters."
                ))
        );

        mockMvc.perform(multipart("/api/v1/drops")
                        .file(filePart())
                        .file(metadataPart(10, EXPIRES_AT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("The supplied password is invalid."))
                .andExpect(jsonPath("$.path").value("/api/v1/drops"))
                .andExpect(jsonPath("$.errors.password.length()").value(2))
                .andExpect(jsonPath("$.errors.password[0]")
                        .value("Password must contain at least eight characters."));
    }

    @Test
    void businessFailureUsesPublicApiErrorContract() throws Exception {
        when(fileDropManagementService.getDetails(DROP_ID, "management-token"))
                .thenThrow(new FileDropNotFoundException());

        mockMvc.perform(get("/api/v1/drops/{id}", DROP_ID)
                        .header("X-Management-Token", "management-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("FILE_DROP_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("The requested file drop was not found."))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/drops/" + DROP_ID));
    }

    @Test
    void technicalFailureDoesNotLeakItsCause() throws Exception {
        when(fileDropManagementService.getDetails(DROP_ID, "management-token"))
                .thenThrow(new DropCreationException(new IOException("secret storage detail")));

        mockMvc.perform(get("/api/v1/drops/{id}", DROP_ID)
                        .header("X-Management-Token", "management-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("DROP_CREATION_FAILED"))
                .andExpect(jsonPath("$.message").value("An internal error occurred."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret storage detail")
                )));
    }

    @Test
    void securityRejectsRoutesOutsideThePublicApi() throws Exception {
        mockMvc.perform(get("/internal/status"))
                .andExpect(status().isForbidden());
    }

    private MockMultipartFile filePart() {
        return new MockMultipartFile(
                "file",
                "report.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "test content".getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile metadataPart(int maxDownloads, Instant expiresAt) {
        String json = """
                {
                  "maxDownloads": %d,
                  "expiresAt": "%s",
                  "password": null
                }
                """.formatted(maxDownloads, expiresAt);

        return new MockMultipartFile(
                "metadata",
                "metadata.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)
        );
    }
}
