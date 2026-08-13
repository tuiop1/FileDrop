package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.internal.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.internal.dto.CreateDropResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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





    }


}
