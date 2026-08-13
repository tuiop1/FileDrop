package dev.tuiop.filedrop.common.exception;

public abstract class BusinessException extends RuntimeException {

    private final String code;
    private final int status;

    protected BusinessException(String code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
