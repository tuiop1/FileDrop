package dev.tuiop.filedrop.storage.internal;

public abstract class StorageException extends RuntimeException {

    protected StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
