package com.neko.repositories;

import com.neko.entity.AuditLog;
import com.neko.enums.AuditAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditLogRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 9, 0);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TestEntityManager entityManager;

    private AuditLog entry(UUID taskId, AuditAction action, LocalDateTime timestamp) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setTaskId(taskId);
        log.setAction(action);
        log.setPerformedBy("vivek");
        log.setTimestamp(timestamp);
        log.setFieldName("status");
        log.setOldValue("PENDING");
        log.setNewValue("COMPLETED");
        log.setDetails("status: PENDING -> COMPLETED");
        return log;
    }

    @Test
    void returnsTheTrailForOneTaskNewestFirst() {
        UUID taskId = UUID.randomUUID();
        AuditLog created = auditLogRepository.saveAndFlush(entry(taskId, AuditAction.CREATE, NOW.minusHours(2)));
        AuditLog completed = auditLogRepository.saveAndFlush(entry(taskId, AuditAction.STATUS_CHANGE, NOW));
        auditLogRepository.saveAndFlush(entry(UUID.randomUUID(), AuditAction.CREATE, NOW));
        entityManager.clear();

        assertThat(auditLogRepository.findByTaskIdOrderByTimestampDesc(taskId))
                .extracting(AuditLog::getId)
                .containsExactly(completed.getId(), created.getId());
    }

    @Test
    void pagesTheTrail() {
        UUID taskId = UUID.randomUUID();
        auditLogRepository.saveAndFlush(entry(taskId, AuditAction.CREATE, NOW.minusHours(2)));
        auditLogRepository.saveAndFlush(entry(taskId, AuditAction.UPDATE, NOW.minusHours(1)));
        auditLogRepository.saveAndFlush(entry(taskId, AuditAction.STATUS_CHANGE, NOW));
        entityManager.clear();

        assertThat(auditLogRepository.findByTaskIdOrderByTimestampDesc(taskId, PageRequest.of(0, 2)))
                .hasSize(2)
                .extracting(AuditLog::getAction)
                .containsExactly(AuditAction.STATUS_CHANGE, AuditAction.UPDATE);
    }

    @Test
    void survivesTheDeletionOfTheTaskItDescribes() {
        // The trail is deliberately not foreign-keyed to task, so history outlives the row.
        UUID taskId = UUID.randomUUID();
        auditLogRepository.saveAndFlush(entry(taskId, AuditAction.DELETE, NOW));
        entityManager.clear();

        assertThat(auditLogRepository.findByTaskIdOrderByTimestampDesc(taskId)).hasSize(1);
    }
}
