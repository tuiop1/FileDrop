package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.TechnicalException;

public final class DropIntegrityException extends TechnicalException {

    public DropIntegrityException(String message) {
        super("DROP_INTEGRITY_CHECK_FAILED", message, null);
    }
}
