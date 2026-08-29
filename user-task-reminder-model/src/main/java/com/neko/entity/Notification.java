package com.neko.entity;

import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "notification",
        indexes = {
                @Index(name = "idx_notification_delivery_status", columnList = "delivery_status"),
                @Index(name = "idx_notification_user", columnList = "user_id"),
                @Index(name = "idx_notification_task", columnList = "task_id")
        })
@ToString(exclude = {"user", "task"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Notification {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(length = 2000)
    private String message;

    private Boolean seen;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 32)
    private DeliveryStatus deliveryStatus;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
}
