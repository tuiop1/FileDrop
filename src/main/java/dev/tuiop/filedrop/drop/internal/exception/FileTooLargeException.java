package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class FileTooLargeException extends BusinessException {

    public FileTooLargeException(long maximumSizeInBytes) {
        super(
                "FILE_TOO_LARGE",
                "The uploaded file exceeds the maximum allowed size of %d bytes.".formatted(maximumSizeInBytes),
                HttpStatus.PAYLOAD_TOO_LARGE.value()
        );
    }
}
