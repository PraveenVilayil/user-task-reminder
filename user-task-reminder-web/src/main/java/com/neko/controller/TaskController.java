package com.neko.controller;

import com.neko.dto.AuditLogDto;
import com.neko.dto.PageResponse;
import com.neko.dto.ReminderDto;
import com.neko.dto.TaskDto;
import com.neko.dto.TaskSearchCriteria;
import com.neko.enums.Priority;
import com.neko.enums.Status;
import com.neko.exceptions.ErrorResponse;
import com.neko.service.AuditService;
import com.neko.service.ReminderService;
import com.neko.service.TaskService;
import com.neko.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.neko.constants.ApiConstants.*;

@RestController
@RequestMapping(TASK_MANAGEMENT + API + V1 + TASK)
@Tag(name = "Tasks", description = "Create, query and maintain tasks")
public class TaskController {

    private final TaskService taskService;
    private final ReminderService reminderService;
    private final AuditService auditService;

    public TaskController(TaskService taskService, ReminderService reminderService, AuditService auditService) {
        this.taskService = taskService;
        this.reminderService = reminderService;
        this.auditService = auditService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a task")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Owner or prerequisite task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskDto> create(
            @Validated(ValidationGroups.OnCreate.class) @RequestBody TaskDto task) {
        return new ResponseEntity<>(taskService.create(task), HttpStatus.CREATED);
    }

    @PatchMapping(path = SLASH + ID_VAR, produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Partially update a task",
            description = "Only the fields present in the body are applied. Moving to COMPLETED is refused "
                    + "while any prerequisite task is still open.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated"),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Prerequisites incomplete",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskDto> update(@PathVariable(ID) UUID id,
                                          @Validated(ValidationGroups.OnUpdate.class) @RequestBody TaskDto task) {
        return new ResponseEntity<>(taskService.update(id, task), HttpStatus.OK);
    }

    @GetMapping(SLASH + ID_VAR)
    @Operation(summary = "Fetch one task by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found"),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskDto> get(@PathVariable(ID) UUID id) {
        return new ResponseEntity<>(taskService.get(id), HttpStatus.OK);
    }

    @GetMapping
    @Operation(summary = "List every task")
    @ApiResponse(responseCode = "200", description = "Tasks returned")
    public ResponseEntity<List<TaskDto>> list() {
        return new ResponseEntity<>(taskService.list(), HttpStatus.OK);
    }

    @GetMapping(SEARCH)
    @Operation(summary = "Search tasks",
            description = "Paged and sorted search. Every filter is optional; omitted filters are not applied. "
                    + "Sortable fields: name, createdDate, modifiedDate, dueDate, priority, status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching page returned"),
            @ApiResponse(responseCode = "400", description = "Unsupported sort property",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<TaskDto>> search(
            @Parameter(description = "Free-text match on name and description") @RequestParam(required = false) String q,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) Priority priority,
            @Parameter(description = "Owner id") @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String label,
            @Parameter(description = "Only tasks past their due date and not completed")
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dueTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort as field,direction", example = "dueDate,asc")
            @RequestParam(defaultValue = "createdDate,desc") String sort) {

        TaskSearchCriteria criteria = TaskSearchCriteria.builder()
                .query(q)
                .status(status)
                .priority(priority)
                .assigneeId(assigneeId)
                .label(label)
                .overdue(overdue)
                .dueFrom(dueFrom)
                .dueTo(dueTo)
                .build();

        return new ResponseEntity<>(
                taskService.search(criteria, PageRequest.of(Math.max(page, 0), clamp(size), parseSort(sort))),
                HttpStatus.OK);
    }

    @GetMapping(SLASH + ID_VAR + AUDIT)
    @Operation(summary = "Audit trail for a task",
            description = "Every recorded change, newest first, including old and new values.")
    @ApiResponse(responseCode = "200", description = "Audit trail returned")
    public ResponseEntity<List<AuditLogDto>> auditTrail(@PathVariable(ID) UUID id) {
        return new ResponseEntity<>(auditService.getTaskAuditTrail(id), HttpStatus.OK);
    }

    @GetMapping(SLASH + ID_VAR + REMINDER)
    @Operation(summary = "Reminders attached to a task")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminders returned"),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ReminderDto>> reminders(@PathVariable(ID) UUID id) {
        return new ResponseEntity<>(reminderService.listByTask(id), HttpStatus.OK);
    }

    @DeleteMapping(SLASH + ID_VAR)
    @Operation(summary = "Delete a task")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted"),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable(ID) UUID id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private int clamp(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, 200);
    }

    /** Accepts {@code field} or {@code field,direction}; defaults to ascending. */
    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = sort.split(",", 2);
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, parts[0].trim());
    }
}
