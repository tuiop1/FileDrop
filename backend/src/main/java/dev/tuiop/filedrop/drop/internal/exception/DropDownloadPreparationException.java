package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.TechnicalException;

public final class DropDownloadPreparationException extends TechnicalException {

    public DropDownloadPreparationException(Throwable cause) {
        super(
                "DROP_DOWNLOAD_PREPARATION_FAILED",
                "Failed to prepare the file drop for download.",
                cause
        );
    }
}
