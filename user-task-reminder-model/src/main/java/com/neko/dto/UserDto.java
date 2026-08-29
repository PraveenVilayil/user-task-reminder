package com.neko.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neko.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
@Schema(name = "User", description = "A person who owns tasks and receives notifications")
public class UserDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotBlank(groups = ValidationGroups.OnCreate.class, message = "userName is required")
    @Size(max = 100, message = "userName must be at most 100 characters")
    @Schema(example = "vivek")
    private String userName;

    @NotBlank(groups = ValidationGroups.OnCreate.class, message = "email is required")
    @Email(message = "email must be a well-formed address")
    @Size(max = 255, message = "email must be at most 255 characters")
    @Schema(example = "vivek@example.com")
    private String email;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdDate;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedDate;

    @Schema(example = "[\"ROLE_USER\"]")
    private List<String> roles;
}
