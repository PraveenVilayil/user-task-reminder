package com.neko.audit;

import com.neko.dto.TaskDto;
import com.neko.service.AuditService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes the task audit trail without any service having to know it exists.
 *
 * <p>Ordered just ahead of Spring transaction advice, so it wraps the
 * transactional service call: the before-snapshot is read before the change
 * starts, and the audit row is written after the change has committed. An audit
 * failure is therefore logged and swallowed rather than rolling the user change
 * back.</p>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TaskAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(TaskAuditAspect.class);

    private final AuditService auditService;
    private final TaskSnapshotReader snapshotReader;

    public TaskAuditAspect(AuditService auditService, TaskSnapshotReader snapshotReader) {
        this.auditService = auditService;
        this.snapshotReader = snapshotReader;
    }

    @Around("execution(* com.neko.service.TaskService.create(..))")
    public Object auditCreate(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof TaskDto created) {
            safely(() -> auditService.recordCreate(created, requestedActor(joinPoint.getArgs(), 0)));
        }
        return result;
    }

    @Around("execution(* com.neko.service.TaskService.update(..))")
    public Object auditUpdate(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID taskId = firstUuid(joinPoint.getArgs());
        TaskDto before = snapshotReader.snapshot(taskId);
        Object result = joinPoint.proceed();
        if (result instanceof TaskDto after) {
            safely(() -> auditService.recordUpdate(before, after, requestedActor(joinPoint.getArgs(), 1)));
        }
        return result;
    }

    @Around("execution(* com.neko.service.TaskService.delete(..))")
    public Object auditDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID taskId = firstUuid(joinPoint.getArgs());
        TaskDto before = snapshotReader.snapshot(taskId);
        Object result = joinPoint.proceed();
        if (before != null) {
            safely(() -> auditService.recordDelete(before, before.getModifiedBy()));
        }
        return result;
    }

    private UUID firstUuid(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    /** The caller can name itself through TaskDto.modifiedBy until auth exists. */
    private String requestedActor(Object[] args, int dtoIndex) {
        if (args.length > dtoIndex && args[dtoIndex] instanceof TaskDto dto) {
            return dto.getModifiedBy();
        }
        return null;
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.error("Failed to write task audit entry", exception);
        }
    }
}
