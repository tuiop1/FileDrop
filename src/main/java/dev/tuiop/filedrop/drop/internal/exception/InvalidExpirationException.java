package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class InvalidExpirationException extends BusinessException {

    public InvalidExpirationException(String reason) {
        super("INVALID_EXPIRATION", reason, HttpStatus.BAD_REQUEST.value());
    }
}
