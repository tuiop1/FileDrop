package dev.tuiop.filedrop.common.internal;

import dev.tuiop.filedrop.common.api.ApiError;
import dev.tuiop.filedrop.common.api.ValidationApiError;
import dev.tuiop.filedrop.common.exception.BusinessException;
import dev.tuiop.filedrop.common.exception.RequestValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ValidationApiError> handleRequestValidationException(
            RequestValidationException exception,
            HttpServletRequest request
    ) {
        ValidationApiError error = ValidationApiError.of(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                request.getRequestURI(),
                exception.errors()
        );

        return ResponseEntity.status(exception.status()).body(error);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ApiError error = ApiError.of(
                exception.status(),
                exception.code(),
                exception.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(exception.status()).body(error);
    }
}
