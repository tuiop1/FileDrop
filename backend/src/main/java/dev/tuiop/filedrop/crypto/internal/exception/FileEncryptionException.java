package dev.tuiop.filedrop.crypto.internal.exception;

import dev.tuiop.filedrop.common.exception.TechnicalException;

public final class FileEncryptionException extends TechnicalException {

    public FileEncryptionException(String message, Throwable cause) {
        super("FILE_ENCRYPTION_FAILED", message, cause);
    }
}
