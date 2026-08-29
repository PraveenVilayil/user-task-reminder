package com.neko.exceptions.ErrorCode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    //start with UTR - {code}
    // 001 - 099 Resource not found
    // 100 - 199 Validation Errors
    // 200 - 299 Internal Server
    // 300 - 399 Unauthorized or auth related
    // 400 - 499 Duplicates or conflicts

    TASK_NOT_FOUND("UTR-001", "Task with ID %s Not Found", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("UTR-002", "User with ID %s Not Found", HttpStatus.NOT_FOUND),
    NOTIFICATION_NOT_FOUND("UTR-003", "Notification with ID %s Not Found", HttpStatus.NOT_FOUND),
    REMINDER_NOT_FOUND("UTR-004", "Reminder with ID %s Not Found", HttpStatus.NOT_FOUND),

    VALIDATION_FAILED("UTR-100", "Request validation failed", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("UTR-101", "Request body could not be parsed: %s", HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER("UTR-102", "Parameter %s has an invalid value", HttpStatus.BAD_REQUEST),
    INVALID_CRON_EXPRESSION("UTR-103", "Cron expression %s is not a valid Spring cron expression", HttpStatus.BAD_REQUEST),
    INVALID_REMINDER_SCHEDULE("UTR-104", "A reminder needs exactly one of cron or dueDate", HttpStatus.BAD_REQUEST),
    REMINDER_DUE_DATE_IN_PAST("UTR-105", "One-time reminder dueDate %s is in the past", HttpStatus.BAD_REQUEST),
    INVALID_SORT_PROPERTY("UTR-106", "Sort property %s is not a sortable task field", HttpStatus.BAD_REQUEST),

    INTERNAL_ERROR("UTR-200", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR),
    NOTIFICATION_DELIVERY_FAILED("UTR-201", "Notification %s could not be delivered", HttpStatus.INTERNAL_SERVER_ERROR),

    DUPLICATE_USER_NAME("UTR-400", "User name %s is already taken", HttpStatus.CONFLICT),
    DUPLICATE_EMAIL("UTR-401", "Email %s is already registered", HttpStatus.CONFLICT),
    TASK_PREREQUISITES_INCOMPLETE("UTR-402", "Task %s cannot be completed while its prerequisites are open: %s", HttpStatus.CONFLICT),
    TASK_PREREQUISITE_CYCLE("UTR-403", "Task %s cannot depend on itself, directly or transitively", HttpStatus.CONFLICT);


    final String code;
    final String message;
    final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

}
