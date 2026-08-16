package dev.tuiop.filedrop.common.internal;

import dev.tuiop.filedrop.common.api.ApiError;
import dev.tuiop.filedrop.common.api.ValidationApiError;
import dev.tuiop.filedrop.common.exception.BusinessException;
import dev.tuiop.filedrop.common.exception.RequestValidationException;
import dev.tuiop.filedrop.common.exception.TechnicalException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TechnicalException.class)
    public ResponseEntity<ApiError> handleTechnicalException(
            TechnicalException exception,
            HttpServletRequest request
    ) {
        String route = resolveRoute(request);

        log.atError()
                .addKeyValue("error.code", exception.code())
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.route", route)
                .setCause(exception)
                .log(
                        "Technical failure {} while handling {} {}",
                        exception.code(),
                        request.getMethod(),
                        route
                );

        ApiError error = ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                exception.code(),
                "An internal error occurred.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

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

    private String resolveRoute(HttpServletRequest request) {
        Object route = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        );
        return route == null ? "/api/**" : route.toString();
    }
}
