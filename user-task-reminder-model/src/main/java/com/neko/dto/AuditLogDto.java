package com.neko.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neko.enums.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AuditLog", description = "A recorded change to a task")
public class AuditLogDto {

    private UUID id;
    private UUID taskId;
    private AuditAction action;
    private String performedBy;
    private LocalDateTime timestamp;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String details;
}
