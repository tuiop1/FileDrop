package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class FileDropExpiredException extends BusinessException {

    public FileDropExpiredException() {
        super(
                "FILE_DROP_EXPIRED",
                "The file drop has expired.",
                HttpStatus.GONE.value()
        );
    }
}
