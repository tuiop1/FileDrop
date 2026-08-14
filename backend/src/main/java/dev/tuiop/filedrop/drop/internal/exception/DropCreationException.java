package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.TechnicalException;

public final class DropCreationException extends TechnicalException {

    public DropCreationException(Throwable cause) {
        super(
                "DROP_CREATION_FAILED",
                "Failed to create the file drop.",
                cause
        );
    }
}
