package dev.tuiop.filedrop.storage.internal;

import dev.tuiop.filedrop.common.exception.TechnicalException;

public abstract class StorageException extends TechnicalException {

    protected StorageException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
