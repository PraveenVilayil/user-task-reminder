package com.neko.exceptions;

import com.neko.exceptions.ErrorCode.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Domain failure carrying a catalogue {@link ErrorCode}. The formatted message
 * is supplied by the code template and the arguments passed here.
 */
@Getter
public class UserTaskReminderException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    public UserTaskReminderException(ErrorCode errorCode, Object... args) {
        super(String.format(errorCode.getMessage(), args));
        this.errorCode = errorCode;
        this.status = errorCode.getStatus();
    }

    public UserTaskReminderException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(String.format(errorCode.getMessage(), args), cause);
        this.errorCode = errorCode;
        this.status = errorCode.getStatus();
    }
}
