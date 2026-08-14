package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.common.internal.ApiExceptionHandler;
import dev.tuiop.filedrop.drop.internal.dto.DropDownloadResult;
import dev.tuiop.filedrop.drop.internal.exception.DownloadPasswordRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class FileDropControllerPasswordTests {

    private static final String TOKEN = "a".repeat(43);
    private static final String PASSWORD = "pässwörd-🔐";
    private static final byte[] FILE_CONTENT = "protected content".getBytes(StandardCharsets.UTF_8);

    @Mock
    private FileDropService fileDropService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new FileDropController(fileDropService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void protectedGetReturnsPasswordChallengeError() throws Exception {
        when(fileDropService.getFileDropAndMetadata(TOKEN))
                .thenThrow(new DownloadPasswordRequiredException());

        mockMvc.perform(get("/api/v1/drops/d/{token}", TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("DOWNLOAD_PASSWORD_REQUIRED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void passwordDownloadAcceptsUnicodeJsonAndStreamsTheFile() throws Exception {
        DropDownloadResult download = new DropDownloadResult(
                "protected.txt",
                MediaType.TEXT_PLAIN_VALUE,
                FILE_CONTENT.length,
                2,
                outputStream -> outputStream.write(FILE_CONTENT)
        );
        when(fileDropService.getFileDropAndMetadata(TOKEN, PASSWORD)).thenReturn(download);

        var initialResult = mockMvc.perform(post("/api/v1/drops/d/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pässwörd-🔐\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(initialResult))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FILE_CONTENT))
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(header().string("X-Downloads-Remaining", "2"));

        verify(fileDropService).getFileDropAndMetadata(TOKEN, PASSWORD);
    }
}
