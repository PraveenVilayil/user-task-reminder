package com.neko.entity;

import com.neko.enums.Priority;
import com.neko.enums.Status;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@Data
@Entity
@Table(name = "task",
        indexes = {
                @Index(name = "idx_task_status", columnList = "status"),
                @Index(name = "idx_task_priority", columnList = "priority"),
                @Index(name = "idx_task_due_date", columnList = "due_date"),
                @Index(name = "idx_task_created_by", columnList = "created_by")
        })
@ToString(exclude = {"notifications", "reminders", "prerequisites"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

    @Id
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "modified_by", length = 100)
    private String modifiedBy;

    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    private Boolean recurring;

    @Column(name = "remainder_id")
    private UUID remainderId;

    /** Stamped once the overdue sweeper has raised a notification, so it does not repeat. */
    @Column(name = "overdue_notified_at")
    private LocalDateTime overdueNotifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Status status;

    @ElementCollection
    @CollectionTable(name = "task_label", joinColumns = @JoinColumn(name = "task_id"))
    @Column(name = "label", nullable = false, length = 100)
    private List<String> labels = new ArrayList<>();

    //TODO: remove transient and enable it to persist when implementing upload logic
    @Transient
    private List<Object> attachments;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reminder> reminders = new ArrayList<>();

    /** Tasks that must reach COMPLETED before this one may be completed. */
    @ManyToMany
    @JoinTable(name = "task_prerequisite",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "prerequisite_task_id"))
    private List<Task> prerequisites = new ArrayList<>();
}
