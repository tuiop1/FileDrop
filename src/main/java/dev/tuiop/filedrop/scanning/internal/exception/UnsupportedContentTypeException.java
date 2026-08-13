package dev.tuiop.filedrop.scanning.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class UnsupportedContentTypeException extends BusinessException {

    public UnsupportedContentTypeException(String contentType) {
        super(
                "UNSUPPORTED_CONTENT_TYPE",
                "Files with content type '%s' are not supported.".formatted(contentType),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
        );
    }
}
