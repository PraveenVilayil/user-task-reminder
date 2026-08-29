package com.neko.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.neko.enums.Channel;
import com.neko.enums.ReminderStatus;
import com.neko.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Reminder", description = "A one-time or recurring prompt attached to a task")
public class ReminderDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotNull(groups = ValidationGroups.OnCreate.class, message = "taskId is required")
    private UUID taskId;

    @Size(max = 120, message = "cron must be at most 120 characters")
    @Schema(description = "Spring cron expression for a recurring reminder", example = "0 0 9 * * MON-FRI")
    private String cron;

    @Size(max = 2000, message = "message must be at most 2000 characters")
    @Schema(example = "Stand-up in 5 minutes")
    private String message;

    @Schema(description = "Fire instant for a one-time reminder", example = "2026-09-01T09:00:00")
    private LocalDateTime dueDate;

    @Schema(description = "Delivery channel; defaults to WEB when omitted")
    private Channel channel;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private ReminderStatus status;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime nextFireTime;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime lastFiredAt;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Integer fireCount;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdDate;

    /**
     * A reminder needs exactly one schedule source: a cron expression for a
     * recurring reminder, or a due date for a one-time one.
     */
    @JsonIgnore
    @AssertTrue(groups = ValidationGroups.OnCreate.class,
            message = "exactly one of cron or dueDate must be provided")
    public boolean isScheduleSpecified() {
        boolean hasCron = cron != null && !cron.isBlank();
        boolean hasDueDate = dueDate != null;
        return hasCron ^ hasDueDate;
    }
}
