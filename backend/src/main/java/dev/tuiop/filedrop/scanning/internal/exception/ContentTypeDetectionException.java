package dev.tuiop.filedrop.scanning.internal.exception;

import dev.tuiop.filedrop.common.exception.TechnicalException;

public final class ContentTypeDetectionException extends TechnicalException {

    public ContentTypeDetectionException(Throwable cause) {
        super(
                "CONTENT_TYPE_DETECTION_FAILED",
                "Failed to detect the uploaded file's content type.",
                cause
        );
    }
}
