package dev.tuiop.filedrop.drop.internal;

import dev.tuiop.filedrop.drop.dto.CreateDropRequest;
import dev.tuiop.filedrop.drop.dto.FileDropResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/v1/drops")
@RestController
@RequiredArgsConstructor
public class FileDropController {

    private final FileDropService fileDropService;
    private final FileDropMapper fileDropMapper;



    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileDropResponse> createDrop(
            @RequestPart("file") MultipartFile file,
          @Valid @RequestPart("metadata")CreateDropRequest request


            ){
    return ResponseEntity.status(HttpStatus.CREATED).body(fileDropMapper.toResponse(fileDropService.create(file,request)));



    }


}
