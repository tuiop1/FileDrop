package dev.tuiop.filedrop.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public abstract class RequestValidationException extends BusinessException {

    private final Map<String, String> errors;

    protected RequestValidationException(String message, Map<String, String> errors) {
        super("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST.value());
        this.errors = Map.copyOf(errors);
    }

    public Map<String, String> errors() {
        return errors;
    }
}
