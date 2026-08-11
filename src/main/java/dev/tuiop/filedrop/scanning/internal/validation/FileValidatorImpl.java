package dev.tuiop.filedrop.scanning.internal.validation;

import dev.tuiop.filedrop.scanning.internal.exception.EmptyFileException;
import dev.tuiop.filedrop.scanning.internal.exception.FileTooLargeException;
import dev.tuiop.filedrop.scanning.internal.exception.InvalidFileNameException;
import dev.tuiop.filedrop.scanning.FileValidator;
import dev.tuiop.filedrop.scanning.internal.exception.MalwareDetectedException;
import dev.tuiop.filedrop.scanning.internal.malwarescan.MalwareScanner;
import dev.tuiop.filedrop.scanning.internal.malwarescan.ScanResult;
import dev.tuiop.filedrop.scanning.internal.malwarescan.ScanStatus;
import dev.tuiop.filedrop.scanning.internal.typedetection.ContentTypeDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
class FileValidatorImpl implements FileValidator {


    @Value("${application.file.max-size}")
    private final DataSize MAX_FILEDROP_SIZE;
    private final ContentTypeDetector contentTypeDetector;
    private final MalwareScanner malwareScanner;

    // validation before saving the file to temporary storage
    @Override
    public void firstFileValidation(MultipartFile file) {


        if (file.isEmpty()) {
            throw new EmptyFileException();
        }

        if (file.getSize() > MAX_FILEDROP_SIZE.toBytes()) {
            throw new FileTooLargeException(MAX_FILEDROP_SIZE.toBytes());
        }

        String fileOriginalName = file.getOriginalFilename();

        if (fileOriginalName == null || fileOriginalName.length() > 255) {

            throw new InvalidFileNameException();

        }


    }

    @Override
    public String preStoreFileValidation(Path path) {


        String contentType = contentTypeDetector.detect(path);

        malwareScanner.scan(path);

        return contentType;


    }

}
