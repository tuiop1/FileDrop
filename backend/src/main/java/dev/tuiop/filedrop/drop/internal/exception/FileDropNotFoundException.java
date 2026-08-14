package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class FileDropNotFoundException extends BusinessException {

    public FileDropNotFoundException() {
        super(
                "FILE_DROP_NOT_FOUND",
                "The requested file drop was not found.",
                HttpStatus.NOT_FOUND.value()
        );
    }
}
