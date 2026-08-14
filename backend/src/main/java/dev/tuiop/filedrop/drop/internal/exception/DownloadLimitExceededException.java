package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class DownloadLimitExceededException extends BusinessException {

    public DownloadLimitExceededException() {
        super(
                "DOWNLOAD_LIMIT_EXCEEDED",
                "The maximum number of downloads has been reached.",
                HttpStatus.GONE.value()
        );
    }
}
