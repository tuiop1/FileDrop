package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropResponse;
import dev.tuiop.filedrop.drop.internal.dto.DropDownloadResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RequestMapping("/api/v1/drops")
@RestController
@RequiredArgsConstructor
public class FileDropController {

    private final FileDropService fileDropService;




    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateDropResponse> createDrop(
            @RequestPart("file") MultipartFile file,
          @Valid @RequestPart("metadata")CreateDropRequest request


            ) {
    return ResponseEntity.status(HttpStatus.CREATED).body(fileDropService.create(file, request));



    }

    @GetMapping("/d/{token}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String token){

       DropDownloadResult result =  fileDropService.getFileDropAndMetadata(token);

        return ResponseEntity.ok().contentType(MediaType.parseMediaType(result.contentType()))
                .contentLength(result.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,
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


}
