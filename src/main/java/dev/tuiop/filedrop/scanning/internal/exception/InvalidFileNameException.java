package dev.tuiop.filedrop.scanning.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class InvalidFileNameException extends BusinessException {

    public InvalidFileNameException() {
        super(
                "INVALID_FILE_NAME",
                "The original file name must be present and no longer than 255 characters.",
                HttpStatus.BAD_REQUEST.value()
        );
    }
}
