package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.RequestValidationException;

import java.util.Map;

public final class InvalidPasswordException extends RequestValidationException {

    public InvalidPasswordException(String reason) {
        super("The supplied password is invalid.", Map.of("password", reason));
    }
}
