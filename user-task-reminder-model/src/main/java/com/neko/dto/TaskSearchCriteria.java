package com.neko.dto;

import com.neko.enums.Priority;
import com.neko.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Filter arguments for the task search endpoint. Every field is optional and
 * null fields are simply not applied.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "TaskSearchCriteria", description = "Optional filters for task search")
public class TaskSearchCriteria {

    @Schema(description = "Free-text match against task name and description", example = "release")
    private String query;

    private Status status;

    private Priority priority;

    @Schema(description = "Owner id to filter by")
    private UUID assigneeId;

    @Schema(description = "Only tasks due at or after this instant")
    private LocalDateTime dueFrom;

    @Schema(description = "Only tasks due at or before this instant")
    private LocalDateTime dueTo;

    @Schema(description = "Only tasks that carry this label")
    private String label;

    @Schema(description = "When true, only tasks past their due date and not completed")
    private Boolean overdue;
}
