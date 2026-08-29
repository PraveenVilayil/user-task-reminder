package com.neko.integration;

import com.neko.dto.AuditLogDto;
import com.neko.dto.TaskDto;
import com.neko.dto.UserDto;
import com.neko.enums.AuditAction;
import com.neko.enums.Priority;
import com.neko.enums.Status;
import com.neko.service.AuditService;
import com.neko.service.TaskService;
import com.neko.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end proof that the audit aspect really fires: no service in this test
 * is told to write an audit entry, yet the trail appears.
 */
@SpringBootTest
@ActiveProfiles("test")
class TaskAuditTrailIT {

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditService auditService;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        UserDto owner = new UserDto();
        owner.setUserName("audit-" + UUID.randomUUID());
        owner.setEmail(owner.getUserName() + "@example.com");
        ownerId = userService.create(owner).getId();
    }

    private TaskDto createTask(String name) {
        TaskDto request = new TaskDto();
        request.setName(name);
        request.setCreatedById(ownerId);
        request.setPriority(Priority.LOW);
        request.setModifiedBy("vivek");
        return taskService.create(request);
    }

    @Test
    void creatingATaskWritesACreateEntry() {
        TaskDto created = createTask("Audited create");

        List<AuditLogDto> trail = auditService.getTaskAuditTrail(created.getId());

        assertThat(trail).hasSize(1);
        assertThat(trail.get(0).getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(trail.get(0).getPerformedBy()).isEqualTo("vivek");
        assertThat(trail.get(0).getTaskId()).isEqualTo(created.getId());
    }

    @Test
    void updatingAFieldWritesTheOldAndNewValues() {
        TaskDto created = createTask("Audited update");

        TaskDto patch = new TaskDto();
        patch.setPriority(Priority.CRITICAL);
        patch.setModifiedBy("praveen");
        taskService.update(created.getId(), patch);

        List<AuditLogDto> trail = auditService.getTaskAuditTrail(created.getId());

        AuditLogDto update = trail.stream()
                .filter(entry -> entry.getAction() == AuditAction.UPDATE)
                .findFirst()
                .orElseThrow();
        assertThat(update.getFieldName()).isEqualTo("priority");
        assertThat(update.getOldValue()).isEqualTo("LOW");
        assertThat(update.getNewValue()).isEqualTo("CRITICAL");
        assertThat(update.getPerformedBy()).isEqualTo("praveen");
    }

    @Test
    void completingATaskWritesADedicatedStatusChangeEntry() {
        TaskDto created = createTask("Audited completion");

        TaskDto patch = new TaskDto();
        patch.setStatus(Status.COMPLETED);
        taskService.update(created.getId(), patch);

        assertThat(auditService.getTaskAuditTrail(created.getId()))
                .extracting(AuditLogDto::getAction)
                .contains(AuditAction.STATUS_CHANGE);

        AuditLogDto statusChange = auditService.getTaskAuditTrail(created.getId()).stream()
                .filter(entry -> entry.getAction() == AuditAction.STATUS_CHANGE)
                .findFirst()
                .orElseThrow();
        assertThat(statusChange.getOldValue()).isEqualTo("PENDING");
        assertThat(statusChange.getNewValue()).isEqualTo("COMPLETED");
    }

    @Test
    void aNoOpUpdateWritesNothing() {
        TaskDto created = createTask("Audited no-op");

        taskService.update(created.getId(), new TaskDto());

        assertThat(auditService.getTaskAuditTrail(created.getId()))
                .extracting(AuditLogDto::getAction)
                .containsExactly(AuditAction.CREATE);
    }

    @Test
    void deletingATaskLeavesItsTrailBehind() {
        TaskDto created = createTask("Audited delete");

        taskService.delete(created.getId());

        assertThat(auditService.getTaskAuditTrail(created.getId()))
                .extracting(AuditLogDto::getAction)
                .containsExactlyInAnyOrder(AuditAction.DELETE, AuditAction.CREATE);
    }

    @Test
    void aRejectedChangeIsNotAudited() {
        TaskDto blocker = createTask("Blocker");
        TaskDto dependent = createTask("Dependent");

        TaskDto linkPatch = new TaskDto();
        linkPatch.setPrerequisiteIds(List.of(blocker.getId()));
        taskService.update(dependent.getId(), linkPatch);

        TaskDto completePatch = new TaskDto();
        completePatch.setStatus(Status.COMPLETED);
        assertThatThrownBy(() -> taskService.update(dependent.getId(), completePatch))
                .isInstanceOf(com.neko.exceptions.UserTaskReminderException.class);

        assertThat(auditService.getTaskAuditTrail(dependent.getId()))
                .extracting(AuditLogDto::getAction)
                .doesNotContain(AuditAction.STATUS_CHANGE);
    }
}
