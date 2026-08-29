package com.neko.exceptions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The single error shape every failing endpoint returns.
 *
 * <p>The {@code code} field carries the project error catalogue value
 * (for example {@code UTR-001}), so clients can branch on a stable identifier
 * rather than on the HTTP status alone.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ErrorResponse", description = "Structured error payload")
public record ErrorResponse(
        @Schema(example = "UTR-001") String code,
        @Schema(example = "Task with ID 1b9d... Not Found") String message,
        @Schema(example = "404") int status,
        @Schema(example = "/taskManagement/api/v1/task/1b9d...") String path,
        LocalDateTime timestamp,
        @Schema(description = "Present when the failure is field-level validation") List<FieldError> fieldErrors) {

    @Schema(name = "FieldError", description = "A single rejected field")
    public record FieldError(
            @Schema(example = "email") String field,
            @Schema(example = "email must be a well-formed address") String message,
            @Schema(example = "not-an-email") Object rejectedValue) {
    }
}
