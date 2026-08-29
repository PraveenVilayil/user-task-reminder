package com.neko.exceptions;

import com.neko.exceptions.ErrorCode.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates every exception the API can raise into the single
 * {@link ErrorResponse} shape, keyed by the {@link ErrorCode} catalogue.
 */
@RestControllerAdvice
public class UserTaskReminderExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UserTaskReminderExceptionHandler.class);

    @ExceptionHandler(UserTaskReminderException.class)
    public ResponseEntity<ErrorResponse> handleUserTaskReminderException(UserTaskReminderException exception,
                                                                        WebRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("Domain failure {}: {}", errorCode.getCode(), exception.getMessage(), exception);
        } else {
            log.debug("Domain failure {}: {}", errorCode.getCode(), exception.getMessage());
        }
        return build(errorCode.getCode(), exception.getMessage(), errorCode.getStatus(), request, null);
    }

    /** Bean Validation failures raised by @Valid / @Validated on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                     WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? new ErrorResponse.FieldError(fieldError.getField(),
                        fieldError.getDefaultMessage(), fieldError.getRejectedValue())
                        : new ErrorResponse.FieldError(error.getObjectName(), error.getDefaultMessage(), null))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED.getCode(), ErrorCode.VALIDATION_FAILED.getMessage(),
                ErrorCode.VALIDATION_FAILED.getStatus(), request, fieldErrors);
    }

    /** Bean Validation failures raised on method parameters (query params, path variables). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
                                                                   WebRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        String.valueOf(violation.getPropertyPath()),
                        violation.getMessage(),
                        violation.getInvalidValue()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .collect(Collectors.toList());
        return build(ErrorCode.VALIDATION_FAILED.getCode(), ErrorCode.VALIDATION_FAILED.getMessage(),
                ErrorCode.VALIDATION_FAILED.getStatus(), request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                              WebRequest request) {
        ErrorCode errorCode = ErrorCode.MALFORMED_REQUEST;
        return build(errorCode.getCode(),
                String.format(errorCode.getMessage(), rootMessage(exception)),
                errorCode.getStatus(), request, null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception exception, WebRequest request) {
        String parameter = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ((MissingServletRequestParameterException) exception).getParameterName();
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;
        return build(errorCode.getCode(), String.format(errorCode.getMessage(), parameter),
                errorCode.getStatus(), request, null);
    }

    /** Static resources and unmapped URLs stay 404 rather than being swallowed by the catch-all below. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception, WebRequest request) {
        return build("UTR-404", "No handler found for " + exception.getResourcePath(),
                HttpStatus.NOT_FOUND, request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, WebRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        log.error("Unhandled exception while serving {}", request.getDescription(false), exception);
        return build(errorCode.getCode(), errorCode.getMessage(), errorCode.getStatus(), request, null);
    }

    private ResponseEntity<ErrorResponse> build(String code, String message, HttpStatus status,
                                                WebRequest request, List<ErrorResponse.FieldError> fieldErrors) {
        ErrorResponse body = new ErrorResponse(code, message, status.value(),
                path(request), LocalDateTime.now(), fieldErrors);
        return new ResponseEntity<>(body, status);
    }

    private String path(WebRequest request) {
        if (request == null) {
            return null;
        }
        // WebRequest renders as "uri=/path"; strip the prefix for a clean value.
        String description = request.getDescription(false);
        return description != null && description.startsWith("uri=") ? description.substring(4) : description;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null) {
            return cause.getClass().getSimpleName();
        }
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }
}
