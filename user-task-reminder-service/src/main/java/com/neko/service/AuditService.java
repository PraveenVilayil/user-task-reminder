package com.neko.service;

import com.neko.dto.AuditLogDto;
import com.neko.dto.TaskDto;
import com.neko.enums.AuditAction;

import java.util.List;
import java.util.UUID;

/**
 * Writes and reads the task change history. Callers never construct
 * {@link com.neko.entity.AuditLog} rows directly.
 */
public interface AuditService {

    /** Records the creation of a task. */
    void recordCreate(TaskDto created, String performedBy);

    /**
     * Diffs before against after and records an UPDATE row for the changed
     * fields, plus a dedicated STATUS_CHANGE row when the status moved.
     * Writes nothing when the two snapshots are equivalent.
     */
    void recordUpdate(TaskDto before, TaskDto after, String performedBy);

    /** Records the deletion of a task. */
    void recordDelete(TaskDto deleted, String performedBy);

    /** Low-level escape hatch used by the higher-level record methods. */
    void record(UUID taskId, AuditAction action, String performedBy,
                String fieldName, String oldValue, String newValue, String details);

    /** Full change history for a task, newest first. */
    List<AuditLogDto> getTaskAuditTrail(UUID taskId);
}
