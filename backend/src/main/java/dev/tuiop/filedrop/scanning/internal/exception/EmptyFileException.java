package dev.tuiop.filedrop.scanning.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class EmptyFileException extends BusinessException {

    public EmptyFileException() {
        super("EMPTY_FILE", "The uploaded file must not be empty.", HttpStatus.BAD_REQUEST.value());
    }
}
