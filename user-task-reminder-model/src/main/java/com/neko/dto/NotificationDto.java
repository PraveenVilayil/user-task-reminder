package com.neko.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import com.neko.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(name = "Notification", description = "A message delivered to a user over a channel")
public class NotificationDto {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotNull(groups = ValidationGroups.OnCreate.class, message = "userId is required")
    private UUID userId;

    private UUID taskId;

    @NotBlank(groups = ValidationGroups.OnCreate.class, message = "message is required")
    @Size(max = 2000, message = "message must be at most 2000 characters")
    @Schema(example = "Reminder: Ship the release notes is due in one hour")
    private String message;

    private Boolean seen;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdDate;

    @Schema(description = "Delivery channel; defaults to WEB when omitted")
    private Channel channel;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private DeliveryStatus deliveryStatus;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Integer deliveryAttempts;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime lastAttemptAt;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String failureReason;
}
