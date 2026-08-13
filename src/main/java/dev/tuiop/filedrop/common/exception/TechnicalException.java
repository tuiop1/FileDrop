package dev.tuiop.filedrop.common.exception;

public abstract class TechnicalException extends RuntimeException {

    private final String code;

    protected TechnicalException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
