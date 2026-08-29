package com.neko.serviceImpl;

import com.neko.TestFixtures;
import com.neko.config.ModelMapperConfig;
import com.neko.dto.AuditLogDto;
import com.neko.dto.TaskDto;
import com.neko.entity.AuditLog;
import com.neko.enums.AuditAction;
import com.neko.enums.Priority;
import com.neko.enums.Status;
import com.neko.repositories.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditServiceImpl auditService;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        auditService = new AuditServiceImpl(auditLogRepository,
                new ModelMapperConfig().modelMapper(), TestFixtures.fixedClock());
        taskId = UUID.randomUUID();
    }

    private TaskDto task(String name, Priority priority, Status status) {
        TaskDto dto = new TaskDto();
        dto.setId(taskId);
        dto.setName(name);
        dto.setPriority(priority);
        dto.setStatus(status);
        return dto;
    }

    @Test
    void recordsACreateWithASnapshotOfTheNewTask() {
        auditService.recordCreate(task("Ship it", Priority.HIGH, Status.PENDING), "vivek");

        AuditLog saved = captureSaved();
        assertThat(saved.getTaskId()).isEqualTo(taskId);
        assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(saved.getPerformedBy()).isEqualTo("vivek");
        assertThat(saved.getTimestamp()).isEqualTo(TestFixtures.NOW);
        assertThat(saved.getDetails()).contains("name=Ship it").contains("status=PENDING");
    }

    @Test
    void recordsASingleFieldUpdateWithOldAndNewValues() {
        auditService.recordUpdate(
                task("Ship it", Priority.LOW, Status.PENDING),
                task("Ship it", Priority.CRITICAL, Status.PENDING),
                "vivek");

        AuditLog saved = captureSaved();
        assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(saved.getFieldName()).isEqualTo("priority");
        assertThat(saved.getOldValue()).isEqualTo("LOW");
        assertThat(saved.getNewValue()).isEqualTo("CRITICAL");
        assertThat(saved.getDetails()).isEqualTo("priority: LOW -> CRITICAL");
    }

    @Test
    void summarisesAMultiFieldUpdateInDetailsAndLeavesFieldNameUnset() {
        auditService.recordUpdate(
                task("Ship it", Priority.LOW, Status.PENDING),
                task("Ship it later", Priority.HIGH, Status.PENDING),
                "vivek");

        AuditLog saved = captureSaved();
        assertThat(saved.getFieldName()).isNull();
        assertThat(saved.getDetails())
                .contains("name: Ship it -> Ship it later")
                .contains("priority: LOW -> HIGH");
    }

    @Test
    void recordsAStatusTransitionAsItsOwnEntry() {
        auditService.recordUpdate(
                task("Ship it", Priority.HIGH, Status.PENDING),
                task("Ship it", Priority.HIGH, Status.COMPLETED),
                "vivek");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog statusChange = captor.getValue();
        assertThat(statusChange.getAction()).isEqualTo(AuditAction.STATUS_CHANGE);
        assertThat(statusChange.getFieldName()).isEqualTo("status");
        assertThat(statusChange.getOldValue()).isEqualTo("PENDING");
        assertThat(statusChange.getNewValue()).isEqualTo("COMPLETED");
    }

    @Test
    void recordsBothAStatusChangeAndAnUpdateWhenOtherFieldsMovedToo() {
        auditService.recordUpdate(
                task("Ship it", Priority.LOW, Status.PENDING),
                task("Ship it", Priority.HIGH, Status.COMPLETED),
                "vivek");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(AuditLog::getAction)
                .containsExactly(AuditAction.STATUS_CHANGE, AuditAction.UPDATE);
    }

    @Test
    void writesNothingWhenTheTwoSnapshotsAreEquivalent() {
        TaskDto unchanged = task("Ship it", Priority.HIGH, Status.PENDING);

        auditService.recordUpdate(unchanged, task("Ship it", Priority.HIGH, Status.PENDING), "vivek");

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void fallsBackToASystemActorWhenNobodyIsNamed() {
        auditService.recordCreate(task("Ship it", Priority.HIGH, Status.PENDING), null);

        assertThat(captureSaved().getPerformedBy()).isEqualTo("system");
    }

    @Test
    void prefersTheModifiedByFieldWhenNoActorIsPassed() {
        TaskDto dto = task("Ship it", Priority.HIGH, Status.PENDING);
        dto.setModifiedBy("praveen");

        auditService.recordCreate(dto, null);

        assertThat(captureSaved().getPerformedBy()).isEqualTo("praveen");
    }

    @Test
    void recordsADeleteWithTheFinalStateOfTheTask() {
        auditService.recordDelete(task("Ship it", Priority.HIGH, Status.IN_PROGRESS), "vivek");

        AuditLog saved = captureSaved();
        assertThat(saved.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(saved.getOldValue()).contains("name=Ship it");
        assertThat(saved.getNewValue()).isNull();
    }

    @Test
    void ignoresSnapshotsWithoutAnIdentifier() {
        auditService.recordCreate(new TaskDto(), "vivek");
        auditService.recordDelete(null, "vivek");
        auditService.recordUpdate(null, task("x", Priority.LOW, Status.PENDING), "vivek");

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void readsTheTrailNewestFirstFromTheRepository() {
        AuditLog entry = new AuditLog();
        entry.setId(UUID.randomUUID());
        entry.setTaskId(taskId);
        entry.setAction(AuditAction.UPDATE);
        entry.setPerformedBy("vivek");
        entry.setTimestamp(TestFixtures.NOW);
        entry.setDetails("priority: LOW -> HIGH");
        when(auditLogRepository.findByTaskIdOrderByTimestampDesc(taskId)).thenReturn(List.of(entry));

        List<AuditLogDto> trail = auditService.getTaskAuditTrail(taskId);

        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(trail.get(0).getDetails()).isEqualTo("priority: LOW -> HIGH");
    }

    private AuditLog captureSaved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
