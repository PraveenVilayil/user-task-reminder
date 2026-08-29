package com.neko.controller;

import com.neko.dto.ReminderDto;
import com.neko.exceptions.ErrorResponse;
import com.neko.service.ReminderService;
import com.neko.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.neko.constants.ApiConstants.*;

@RestController
@RequestMapping(TASK_MANAGEMENT + API + V1 + REMINDER)
@Tag(name = "Reminders",
        description = "One-time and recurring prompts. When a reminder fires it creates a notification.")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Schedule a reminder",
            description = "Supply exactly one of cron (recurring) or dueDate (one-time). "
                    + "The response carries the computed nextFireTime.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reminder scheduled"),
            @ApiResponse(responseCode = "400", description = "Invalid cron, past due date, or both/neither supplied",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReminderDto> create(
            @Validated(ValidationGroups.OnCreate.class) @RequestBody ReminderDto reminder) {
        return new ResponseEntity<>(reminderService.create(reminder), HttpStatus.CREATED);
    }

    @PatchMapping(path = SLASH + ID_VAR, produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Partially update a reminder",
            description = "Changing cron or dueDate recomputes nextFireTime immediately.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminder updated"),
            @ApiResponse(responseCode = "400", description = "Invalid schedule",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reminder not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReminderDto> update(
            @PathVariable(ID) UUID id,
            @Validated(ValidationGroups.OnUpdate.class) @RequestBody ReminderDto reminder) {
        return new ResponseEntity<>(reminderService.update(id, reminder), HttpStatus.OK);
    }

    @PostMapping(path = SLASH + ID_VAR + CANCEL, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cancel a reminder",
            description = "Stops future firing and clears nextFireTime, keeping the row and its fire history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminder cancelled"),
            @ApiResponse(responseCode = "404", description = "Reminder not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReminderDto> cancel(@PathVariable(ID) UUID id) {
        return new ResponseEntity<>(reminderService.cancel(id), HttpStatus.OK);
    }

    @GetMapping(path = SLASH + ID_VAR)
    @Operation(summary = "Fetch one reminder by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminder found"),
            @ApiResponse(responseCode = "404", description = "Reminder not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReminderDto> get(@PathVariable(ID) UUID id) {
        return new ResponseEntity<>(reminderService.get(id), HttpStatus.OK);
    }

    @GetMapping
    @Operation(summary = "List every reminder")
    @ApiResponse(responseCode = "200", description = "Reminders returned")
    public ResponseEntity<List<ReminderDto>> list() {
        return new ResponseEntity<>(reminderService.list(), HttpStatus.OK);
    }

    @DeleteMapping(path = SLASH + ID_VAR)
    @Operation(summary = "Delete a reminder")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reminder deleted"),
            @ApiResponse(responseCode = "404", description = "Reminder not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable(ID) UUID id) {
        reminderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
