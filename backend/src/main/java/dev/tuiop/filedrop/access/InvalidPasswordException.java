package dev.tuiop.filedrop.access;

import dev.tuiop.filedrop.common.exception.RequestValidationException;

import java.util.List;
import java.util.Map;

public final class InvalidPasswordException extends RequestValidationException {

    public InvalidPasswordException(List<String> reasons) {
        super("The supplied password is invalid.", Map.of("password", reasons));
    }
}
