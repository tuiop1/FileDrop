package dev.tuiop.filedrop.storage.internal.temporary.exception;

import dev.tuiop.filedrop.storage.internal.StorageException;

public final class TemporaryFileStorageException extends StorageException {

    public TemporaryFileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
