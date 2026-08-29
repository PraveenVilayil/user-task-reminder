package com.neko.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neko.enums.Priority;
import com.neko.enums.Status;
import com.neko.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Task", description = "A unit of work owned by a user")
public class TaskDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotBlank(groups = ValidationGroups.OnCreate.class, message = "name is required")
    @Size(max = 255, message = "name must be at most 255 characters")
    @Schema(example = "Ship the release notes")
    private String name;

    @Size(max = 2000, message = "description must be at most 2000 characters")
    private String description;

    /** Owner of the task. Required on create. */
    @NotNull(groups = ValidationGroups.OnCreate.class, message = "createdById is required")
    @Schema(description = "Id of the owning user", example = "8b1f0f2c-6f4a-4a1e-9a0f-2b7f4c8d9e10")
    private UUID createdById;

    /** Resolved owner, populated on reads only. */
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UserDto createdBy;

    @Size(max = 100)
    private String modifiedBy;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdDate;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime modifiedDate;

    private LocalDateTime dueDate;

    private Boolean recurring;

    private UUID remainderId;

    private Priority priority;

    private Status status;

    private List<String> labels;

    /** Tasks that must be COMPLETED before this task may be completed. */
    private List<UUID> prerequisiteIds;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "True when the due date has passed and the task is not completed")
    private Boolean overdue;

    private List<Object> attachments;
}
