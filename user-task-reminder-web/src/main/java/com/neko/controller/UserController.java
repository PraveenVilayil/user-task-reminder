package com.neko.controller;

import com.neko.dto.NotificationDto;
import com.neko.dto.UserDto;
import com.neko.exceptions.ErrorResponse;
import com.neko.service.NotificationService;
import com.neko.service.UserService;
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
@RequestMapping(TASK_MANAGEMENT + API + V1 + USER)
@Tag(name = "Users", description = "Task owners and notification recipients")
public class UserController {

    private final UserService userService;
    private final NotificationService notificationService;

    public UserController(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "User name or email already taken",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserDto> create(
            @Validated(ValidationGroups.OnCreate.class) @RequestBody UserDto user) {
        return new ResponseEntity<>(userService.create(user), HttpStatus.CREATED);
    }

    @PatchMapping(path = SLASH + ID_VAR, produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Partially update a user",
            description = "Only the fields present in the body are applied.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserDto> update(@PathVariable(ID) UUID id,
                                          @Validated(ValidationGroups.OnUpdate.class) @RequestBody UserDto user) {
        return new ResponseEntity<>(userService.update(id, user), HttpStatus.OK);
    }

    @GetMapping(path = SLASH + ID_VAR)
    @Operation(summary = "Fetch one user by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserDto> get(@PathVariable(ID) UUID id) {
        return new ResponseEntity<>(userService.get(id), HttpStatus.OK);
    }

    @GetMapping
    @Operation(summary = "List every user")
    @ApiResponse(responseCode = "200", description = "Users returned")
    public ResponseEntity<List<UserDto>> list() {
        return new ResponseEntity<>(userService.list(), HttpStatus.OK);
    }

    @GetMapping(path = SLASH + ID_VAR + NOTIFICATION)
    @Operation(summary = "Notifications for a user, newest first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications returned"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<NotificationDto>> notifications(@PathVariable(ID) UUID id) {
        return new ResponseEntity<>(notificationService.getByUser(id), HttpStatus.OK);
    }

    @DeleteMapping(path = SLASH + ID_VAR)
    @Operation(summary = "Delete a user")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable(ID) UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
