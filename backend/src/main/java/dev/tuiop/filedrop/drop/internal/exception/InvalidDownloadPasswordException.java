package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class InvalidDownloadPasswordException extends BusinessException {

    public InvalidDownloadPasswordException() {
        super(
                "INVALID_DOWNLOAD_PASSWORD",
                "The supplied download password is incorrect.",
                HttpStatus.UNAUTHORIZED.value()
        );
    }
}
