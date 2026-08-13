package dev.tuiop.filedrop.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class RequestValidationException extends BusinessException {

    private final Map<String, List<String>> errors;

    protected RequestValidationException(String message, Map<String, List<String>> errors) {
        super("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST.value());

        Map<String, List<String>> errorsCopy = new LinkedHashMap<>();
        errors.forEach((field, messages) -> errorsCopy.put(field, List.copyOf(messages)));
        this.errors = Collections.unmodifiableMap(errorsCopy);
    }

    public Map<String, List<String>> errors() {
        return errors;
    }
}
