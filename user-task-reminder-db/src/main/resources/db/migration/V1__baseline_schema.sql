-- Baseline schema for user-task-reminder.
-- Written in portable SQL so the same migration runs on both PostgreSQL (prod)
-- and H2 (dev and test). Hibernate never generates DDL; this file is the source
-- of truth for the schema.

CREATE TABLE user_entity (
    id           UUID          NOT NULL,
    user_name    VARCHAR(100)  NOT NULL,
    email        VARCHAR(255)  NOT NULL,
    first_name   VARCHAR(100),
    last_name    VARCHAR(100),
    created_date TIMESTAMP,
    updated_date TIMESTAMP,
    CONSTRAINT pk_user_entity PRIMARY KEY (id),
    CONSTRAINT uq_user_entity_user_name UNIQUE (user_name),
    CONSTRAINT uq_user_entity_email UNIQUE (email)
);

CREATE TABLE user_role (
    user_id UUID        NOT NULL,
    role    VARCHAR(64) NOT NULL,
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user_entity (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_role_user_id ON user_role (user_id);

CREATE TABLE task (
    id                  UUID         NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         VARCHAR(2000),
    created_date        TIMESTAMP,
    created_by          UUID,
    modified_by         VARCHAR(100),
    modified_date       TIMESTAMP,
    due_date            TIMESTAMP,
    recurring           BOOLEAN,
    remainder_id        UUID,
    overdue_notified_at TIMESTAMP,
    priority            VARCHAR(32),
    status              VARCHAR(32),
    CONSTRAINT pk_task PRIMARY KEY (id),
    CONSTRAINT fk_task_created_by FOREIGN KEY (created_by) REFERENCES user_entity (id)
);

CREATE INDEX idx_task_status ON task (status);
CREATE INDEX idx_task_priority ON task (priority);
CREATE INDEX idx_task_due_date ON task (due_date);
CREATE INDEX idx_task_created_by ON task (created_by);

CREATE TABLE task_label (
    task_id UUID         NOT NULL,
    label   VARCHAR(100) NOT NULL,
    CONSTRAINT fk_task_label_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
);

CREATE INDEX idx_task_label_task_id ON task_label (task_id);
CREATE INDEX idx_task_label_label ON task_label (label);

-- Self-referencing dependency edges: a task may not be completed while any of
-- its prerequisites is still open.
CREATE TABLE task_prerequisite (
    task_id              UUID NOT NULL,
    prerequisite_task_id UUID NOT NULL,
    CONSTRAINT pk_task_prerequisite PRIMARY KEY (task_id, prerequisite_task_id),
    CONSTRAINT fk_task_prerequisite_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_prerequisite_target FOREIGN KEY (prerequisite_task_id) REFERENCES task (id) ON DELETE CASCADE
);

CREATE TABLE reminder (
    id             UUID        NOT NULL,
    task_id        UUID,
    cron           VARCHAR(120),
    message        VARCHAR(2000),
    due_date       TIMESTAMP,
    channel        VARCHAR(32),
    status         VARCHAR(32) NOT NULL,
    next_fire_time TIMESTAMP,
    last_fired_at  TIMESTAMP,
    fire_count     INTEGER     NOT NULL DEFAULT 0,
    created_date   TIMESTAMP,
    CONSTRAINT pk_reminder PRIMARY KEY (id),
    CONSTRAINT fk_reminder_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
);

-- The scheduler polls on next_fire_time, so it carries the hot index.
CREATE INDEX idx_reminder_next_fire_time ON reminder (next_fire_time);
CREATE INDEX idx_reminder_task ON reminder (task_id);
CREATE INDEX idx_reminder_status ON reminder (status);

CREATE TABLE notification (
    id                UUID    NOT NULL,
    user_id           UUID,
    task_id           UUID,
    message           VARCHAR(2000),
    seen              BOOLEAN,
    created_date      TIMESTAMP,
    channel           VARCHAR(32),
    delivery_status   VARCHAR(32),
    delivery_attempts INTEGER NOT NULL DEFAULT 0,
    last_attempt_at   TIMESTAMP,
    failure_reason    VARCHAR(1000),
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES user_entity (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_task FOREIGN KEY (task_id) REFERENCES task (id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_delivery_status ON notification (delivery_status);
CREATE INDEX idx_notification_user ON notification (user_id);
CREATE INDEX idx_notification_task ON notification (task_id);

-- Append-only. Deliberately not foreign-keyed to task: the trail must survive
-- the deletion of the task it describes.
CREATE TABLE audit_log (
    id              UUID        NOT NULL,
    task_id         UUID        NOT NULL,
    action          VARCHAR(32) NOT NULL,
    performed_by    VARCHAR(100),
    event_timestamp TIMESTAMP   NOT NULL,
    field_name      VARCHAR(100),
    old_value       VARCHAR(2000),
    new_value       VARCHAR(2000),
    details         VARCHAR(4000),
    CONSTRAINT pk_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_audit_log_task_id ON audit_log (task_id);
CREATE INDEX idx_audit_log_timestamp ON audit_log (event_timestamp);
