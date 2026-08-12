package dev.tuiop.filedrop.storage.internal.object.exception;

import dev.tuiop.filedrop.storage.internal.StorageException;

public final class ObjectStorageException extends StorageException {

    public ObjectStorageException(String message, Throwable cause) {
        super("OBJECT_STORAGE_FAILURE", message, cause);
    }
}
