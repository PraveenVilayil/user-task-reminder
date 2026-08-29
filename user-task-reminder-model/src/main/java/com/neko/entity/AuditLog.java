package com.neko.entity;

import com.neko.enums.AuditAction;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable record of a change made to a {@link Task}: who did what, when,
 * and the old to new values involved.
 */
@NoArgsConstructor
@Data
@Entity
@Table(name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_log_task_id", columnList = "task_id"),
                @Index(name = "idx_audit_log_timestamp", columnList = "event_timestamp")
        })
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditLog {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditAction action;

    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime timestamp;

    /** Set for single-field changes such as a status transition. */
    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "old_value", length = 2000)
    private String oldValue;

    @Column(name = "new_value", length = 2000)
    private String newValue;

    /** Human-readable change summary, e.g. priority: LOW to HIGH. */
    @Column(length = 4000)
    private String details;
}
