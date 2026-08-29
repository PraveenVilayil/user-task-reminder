package com.neko.entity;

import com.neko.enums.Channel;
import com.neko.enums.ReminderStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A scheduled prompt attached to a {@link Task}.
 *
 * <p>A reminder is either <em>one-time</em> (no cron; fires once at its due date)
 * or <em>recurring</em> (a Spring cron expression yielding successive fire times).
 * {@code nextFireTime} is the single source of truth the scheduler polls; it is
 * recomputed after every fire.</p>
 */
@NoArgsConstructor
@Data
@Entity
@Table(name = "reminder",
        indexes = {
                @Index(name = "idx_reminder_next_fire_time", columnList = "next_fire_time"),
                @Index(name = "idx_reminder_task", columnList = "task_id"),
                @Index(name = "idx_reminder_status", columnList = "status")
        })
@ToString(exclude = "task")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Reminder {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private Task task;

    /** Spring cron expression for recurring reminders; null for one-time ones. */
    @Column(length = 120)
    private String cron;

    @Column(length = 2000)
    private String message;

    /** Fire instant for a one-time reminder; the start boundary for a recurring one. */
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ReminderStatus status;

    @Column(name = "next_fire_time")
    private LocalDateTime nextFireTime;

    @Column(name = "last_fired_at")
    private LocalDateTime lastFiredAt;

    @Column(name = "fire_count", nullable = false)
    private int fireCount;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    public boolean isRecurring() {
        return cron != null && !cron.isBlank();
    }
}
