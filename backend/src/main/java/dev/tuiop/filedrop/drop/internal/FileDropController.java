package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropResponse;
import dev.tuiop.filedrop.drop.internal.dto.DownloadDropRequest;
import dev.tuiop.filedrop.drop.internal.dto.DropDownloadResult;
import dev.tuiop.filedrop.drop.internal.dto.FileDropDetailsResponse;
import dev.tuiop.filedrop.drop.internal.dto.UpdateExpirationRequest;
import dev.tuiop.filedrop.drop.internal.dto.UpdateMaxDownloadsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RequestMapping("/api/v1/drops")
@RestController
@RequiredArgsConstructor
public class FileDropController {

    private final FileDropService fileDropService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateDropResponse> createDrop(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") CreateDropRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fileDropService.create(file, request));
    }

    @GetMapping("/d/{token}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String token) {
        return downloadResponse(fileDropService.getFileDropAndMetadata(token));
    }

    @PostMapping(value = "/d/{token}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StreamingResponseBody> downloadWithPassword(
            @PathVariable String token,
            @RequestBody DownloadDropRequest request
    ) {
        return downloadResponse(
                fileDropService.getFileDropAndMetadata(
                        token,
                        request == null ? null : request.password()
                )
        );
    }

    private ResponseEntity<StreamingResponseBody> downloadResponse(DropDownloadResult result) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.size())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(result.filename(), StandardCharsets.UTF_8)
                                .build().toString()
                )
                .header(
                        "X-Downloads-Remaining",
                        String.valueOf(result.downloadsRemaining())
                )
                .body(result.body());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileDropDetailsResponse> getDetails(
            @PathVariable UUID id,
            @RequestHeader("X-Management-Token") String managementToken
    ) {
        return ResponseEntity.ok(fileDropService.getDetails(id, managementToken));
    }

    @PatchMapping("/{id}/expiration")
    public ResponseEntity<FileDropDetailsResponse> updateExpiration(
            @PathVariable UUID id,
            @RequestHeader("X-Management-Token") String managementToken,
            @Valid @RequestBody UpdateExpirationRequest request
    ) {
        return ResponseEntity.ok(fileDropService.updateExpiration(id, managementToken, request));
    }

    @PatchMapping("/{id}/max-downloads")
    public ResponseEntity<FileDropDetailsResponse> updateMaxDownloads(
            @PathVariable UUID id,
            @RequestHeader("X-Management-Token") String managementToken,
            @Valid @RequestBody UpdateMaxDownloadsRequest request
    ) {
        return ResponseEntity.ok(fileDropService.updateMaxDownloads(id, managementToken, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader("X-Management-Token") String managementToken
    ) {
        fileDropService.requestDeletion(id, managementToken);
        return ResponseEntity.noContent().build();
    }
}
