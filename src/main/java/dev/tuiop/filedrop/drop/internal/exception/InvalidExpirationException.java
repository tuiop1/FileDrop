package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.RequestValidationException;

import java.util.Map;

public final class InvalidExpirationException extends RequestValidationException {

    public InvalidExpirationException(String reason) {
        super("The supplied expiration time is invalid.", Map.of("expiresAt", reason));
    }
}
