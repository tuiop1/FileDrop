package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class DownloadPasswordRequiredException extends BusinessException {

    public DownloadPasswordRequiredException() {
        super(
                "DOWNLOAD_PASSWORD_REQUIRED",
                "A password is required to download this file.",
                HttpStatus.UNAUTHORIZED.value()
        );
    }
}
