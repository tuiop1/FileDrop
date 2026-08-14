package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class InvalidMaxDownloadsException extends BusinessException {

    public InvalidMaxDownloadsException(String reason) {
        super("INVALID_MAX_DOWNLOADS", reason, HttpStatus.BAD_REQUEST.value());
    }
}
