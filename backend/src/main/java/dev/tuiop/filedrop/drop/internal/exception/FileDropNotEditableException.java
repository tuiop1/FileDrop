package dev.tuiop.filedrop.drop.internal.exception;

import dev.tuiop.filedrop.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class FileDropNotEditableException extends BusinessException {

    public FileDropNotEditableException() {
        super(
                "FILE_DROP_NOT_EDITABLE",
                "Only an available, unexpired file drop can be modified.",
                HttpStatus.CONFLICT.value()
        );
    }
}
