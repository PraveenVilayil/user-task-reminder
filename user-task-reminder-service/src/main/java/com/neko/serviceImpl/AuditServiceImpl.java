package com.neko.serviceImpl;

import com.neko.dto.AuditLogDto;
import com.neko.dto.TaskDto;
import com.neko.entity.AuditLog;
import com.neko.enums.AuditAction;
import com.neko.repositories.AuditLogRepository;
import com.neko.service.AuditService;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Persists the task change history.
 *
 * <p>Writes run in their own transaction ({@code REQUIRES_NEW}): an audit
 * failure must never roll back the business change that produced it, and the
 * aspect that drives this service runs after the task change has committed.</p>
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private static final String SYSTEM_ACTOR = "system";
    private static final int MAX_VALUE_LENGTH = 2000;

    /** Fields whose changes are worth recording, in the order they are reported. */
    private static final Map<String, Function<TaskDto, Object>> AUDITED_FIELDS = new LinkedHashMap<>();

    static {
        AUDITED_FIELDS.put("name", TaskDto::getName);
        AUDITED_FIELDS.put("description", TaskDto::getDescription);
        AUDITED_FIELDS.put("priority", TaskDto::getPriority);
        AUDITED_FIELDS.put("dueDate", TaskDto::getDueDate);
        AUDITED_FIELDS.put("recurring", TaskDto::getRecurring);
        AUDITED_FIELDS.put("labels", TaskDto::getLabels);
        AUDITED_FIELDS.put("createdById", TaskDto::getCreatedById);
        AUDITED_FIELDS.put("prerequisiteIds", TaskDto::getPrerequisiteIds);
    }

    private final AuditLogRepository auditLogRepository;
    private final ModelMapper mapper;
    private final Clock clock;

    public AuditServiceImpl(AuditLogRepository auditLogRepository, ModelMapper mapper, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCreate(TaskDto created, String performedBy) {
        if (created == null || created.getId() == null) {
            return;
        }
        record(created.getId(), AuditAction.CREATE, actor(performedBy, created),
                null, null, null, summaryOf(created));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUpdate(TaskDto before, TaskDto after, String performedBy) {
        if (before == null || after == null || after.getId() == null) {
            return;
        }
        String actor = actor(performedBy, after);

        // A status transition gets its own row so it can be queried on its own.
        if (!Objects.equals(before.getStatus(), after.getStatus())) {
            record(after.getId(), AuditAction.STATUS_CHANGE, actor, "status",
                    stringify(before.getStatus()), stringify(after.getStatus()),
                    "status: " + stringify(before.getStatus()) + " -> " + stringify(after.getStatus()));
        }

        List<String> changes = new ArrayList<>();
        String singleField = null;
        String singleOld = null;
        String singleNew = null;
        for (Map.Entry<String, Function<TaskDto, Object>> field : AUDITED_FIELDS.entrySet()) {
            Object oldValue = field.getValue().apply(before);
            Object newValue = field.getValue().apply(after);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            changes.add(field.getKey() + ": " + stringify(oldValue) + " -> " + stringify(newValue));
            singleField = field.getKey();
            singleOld = stringify(oldValue);
            singleNew = stringify(newValue);
        }

        if (changes.isEmpty()) {
            return;
        }
        boolean single = changes.size() == 1;
        record(after.getId(), AuditAction.UPDATE, actor,
                single ? singleField : null,
                single ? singleOld : null,
                single ? singleNew : null,
                String.join(" | ", changes));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDelete(TaskDto deleted, String performedBy) {
        if (deleted == null || deleted.getId() == null) {
            return;
        }
        record(deleted.getId(), AuditAction.DELETE, actor(performedBy, deleted),
                null, summaryOf(deleted), null, summaryOf(deleted));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID taskId, AuditAction action, String performedBy,
                       String fieldName, String oldValue, String newValue, String details) {
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID());
        entry.setTaskId(taskId);
        entry.setAction(action);
        entry.setPerformedBy(performedBy == null ? SYSTEM_ACTOR : truncate(performedBy, 100));
        entry.setTimestamp(LocalDateTime.now(clock));
        entry.setFieldName(fieldName);
        entry.setOldValue(truncate(oldValue, MAX_VALUE_LENGTH));
        entry.setNewValue(truncate(newValue, MAX_VALUE_LENGTH));
        entry.setDetails(truncate(details, 4000));
        auditLogRepository.save(entry);
        log.debug("Audit {} on task {} by {}", action, taskId, entry.getPerformedBy());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditLogDto> getTaskAuditTrail(UUID taskId) {
        return auditLogRepository.findByTaskIdOrderByTimestampDesc(taskId).stream()
                .map(entry -> mapper.map(entry, AuditLogDto.class))
                .collect(Collectors.toList());
    }

    /**
     * There is no authentication layer yet, so the actor is taken from the
     * request when supplied and falls back to a system marker.
     */
    private String actor(String performedBy, TaskDto task) {
        if (performedBy != null && !performedBy.isBlank()) {
            return performedBy;
        }
        if (task.getModifiedBy() != null && !task.getModifiedBy().isBlank()) {
            return task.getModifiedBy();
        }
        if (task.getCreatedBy() != null && task.getCreatedBy().getUserName() != null) {
            return task.getCreatedBy().getUserName();
        }
        return SYSTEM_ACTOR;
    }

    private String summaryOf(TaskDto task) {
        return AUDITED_FIELDS.entrySet().stream()
                .map(field -> field.getKey() + "=" + stringify(field.getValue().apply(task)))
                .collect(Collectors.joining(" | ", "status=" + stringify(task.getStatus()) + " | ", ""));
    }

    private String stringify(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
