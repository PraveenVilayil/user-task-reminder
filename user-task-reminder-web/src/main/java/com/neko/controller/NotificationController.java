package com.neko.controller;

import com.neko.dto.NotificationDto;
import com.neko.exceptions.ErrorResponse;
import com.neko.service.NotificationService;
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
@RequestMapping(TASK_MANAGEMENT + API + V1 + NOTIFICATION)
@Tag(name = "Notifications", description = "Messages raised for users, and their delivery state")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create and dispatch a notification",
            description = "The notification is handed to the sender registered for its channel immediately; "
                    + "the response carries the resulting delivery status.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Notification created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User or task not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<NotificationDto> create(
            @Validated(ValidationGroups.OnCreate.class) @RequestBody NotificationDto notification) {
        return new ResponseEntity<>(notificationService.create(notification), HttpStatus.CREATED);
    }

    @PatchMapping(path = SLASH + ID_VAR, produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Partially update a notification",
            description = "Typically used to mark a notification seen. Delivery bookkeeping is server-owned "
                    + "and cannot be set through this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification updated"),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<NotificationDto> update(
            @PathVariable(ID) UUID id,
            @Validated(ValidationGroups.OnUpdate.class) @RequestBody NotificationDto notification) {
        return new ResponseEntity<>(notificationService.update(id, notification), HttpStatus.OK);
    }

    @GetMapping(path = SLASH + ID_VAR)
    @Operation(summary = "Fetch one notification by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification found"),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<NotificationDto> get(@PathVariable(ID) UUID id) {
        return new ResponseEntity<>(notificationService.getById(id), HttpStatus.OK);
    }

    @GetMapping
    @Operation(summary = "List every notification")
    @ApiResponse(responseCode = "200", description = "Notifications returned")
    public ResponseEntity<List<NotificationDto>> list() {
        return new ResponseEntity<>(notificationService.get(), HttpStatus.OK);
    }

    @DeleteMapping(path = SLASH + ID_VAR)
    @Operation(summary = "Delete a notification")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notification deleted"),
            @ApiResponse(responseCode = "404", description = "Notification not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable(ID) UUID id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
