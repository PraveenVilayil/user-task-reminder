package com.neko.audit;

import com.neko.dto.PageResponse;
import com.neko.dto.TaskDto;
import com.neko.dto.TaskSearchCriteria;
import com.neko.enums.Status;
import com.neko.service.AuditService;
import com.neko.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The aspect is what makes auditing automatic, so it is exercised through a real
 * AspectJ proxy over a stub service rather than by calling its advice directly.
 */
@ExtendWith(MockitoExtension.class)
class TaskAuditAspectTest {

    @Mock
    private AuditService auditService;

    @Mock
    private TaskSnapshotReader snapshotReader;

    private StubTaskService target;
    private TaskService proxied;

    /** Minimal in-memory TaskService so the advice has something real to wrap. */
    private static final class StubTaskService implements TaskService {

        private final List<TaskDto> created = new ArrayList<>();
        private TaskDto updateResult;
        private boolean deleted;
        private RuntimeException failure;

        @Override
        public TaskDto create(TaskDto task) {
            if (failure != null) {
                throw failure;
            }
            TaskDto result = new TaskDto();
            result.setId(UUID.randomUUID());
            result.setName(task.getName());
            result.setModifiedBy(task.getModifiedBy());
            created.add(result);
            return result;
        }

        @Override
        public TaskDto get(UUID id) {
            return null;
        }

        @Override
        public List<TaskDto> list() {
            return List.of();
        }

        @Override
        public PageResponse<TaskDto> search(TaskSearchCriteria criteria, Pageable pageable) {
            return PageResponse.of(List.of(), 0, 20, 0);
        }

        @Override
        public TaskDto update(UUID id, TaskDto task) {
            if (failure != null) {
                throw failure;
            }
            return updateResult;
        }

        @Override
        public void delete(UUID id) {
            if (failure != null) {
                throw failure;
            }
            deleted = true;
        }
    }

    @BeforeEach
    void setUp() {
        target = new StubTaskService();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new TaskAuditAspect(auditService, snapshotReader));
        proxied = factory.getProxy();
    }

    @Test
    void recordsACreateAfterTheServiceReturns() {
        TaskDto request = new TaskDto();
        request.setName("Ship it");
        request.setModifiedBy("vivek");

        TaskDto created = proxied.create(request);

        verify(auditService).recordCreate(created, "vivek");
    }

    @Test
    void recordsAnUpdateWithTheBeforeAndAfterSnapshots() {
        UUID taskId = UUID.randomUUID();
        TaskDto before = new TaskDto();
        before.setId(taskId);
        before.setStatus(Status.PENDING);
        TaskDto after = new TaskDto();
        after.setId(taskId);
        after.setStatus(Status.COMPLETED);

        target.updateResult = after;
        when(snapshotReader.snapshot(taskId)).thenReturn(before);

        TaskDto patch = new TaskDto();
        patch.setStatus(Status.COMPLETED);
        patch.setModifiedBy("praveen");

        proxied.update(taskId, patch);

        verify(auditService).recordUpdate(before, after, "praveen");
    }

    @Test
    void takesTheBeforeSnapshotWhileTheTaskStillExists() {
        UUID taskId = UUID.randomUUID();
        TaskDto before = new TaskDto();
        before.setId(taskId);
        before.setModifiedBy("vivek");
        when(snapshotReader.snapshot(taskId)).thenReturn(before);

        proxied.delete(taskId);

        assertThat(target.deleted).isTrue();
        verify(auditService).recordDelete(before, "vivek");
    }

    @Test
    void writesNothingWhenTheTaskBeingDeletedIsNotFound() {
        UUID taskId = UUID.randomUUID();
        when(snapshotReader.snapshot(taskId)).thenReturn(null);

        proxied.delete(taskId);

        verify(auditService, never()).recordDelete(any(), any());
    }

    @Test
    void doesNotAuditAFailedOperation() {
        target.failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> proxied.create(new TaskDto())).isInstanceOf(IllegalStateException.class);

        verify(auditService, never()).recordCreate(any(), any());
    }

    @Test
    void neverLetsAnAuditFailureBreakTheUserOperation() {
        doThrow(new IllegalStateException("audit store is down"))
                .when(auditService).recordCreate(any(), eq("vivek"));

        TaskDto request = new TaskDto();
        request.setName("Ship it");
        request.setModifiedBy("vivek");

        TaskDto created = proxied.create(request);

        assertThat(created.getName()).isEqualTo("Ship it");
    }

    @Test
    void leavesUnauditedMethodsAlone() {
        proxied.list();
        proxied.get(UUID.randomUUID());

        verify(auditService, never()).recordCreate(any(), any());
        verify(auditService, never()).recordUpdate(any(), any(), any());
        verify(auditService, never()).recordDelete(any(), any());
    }
}
