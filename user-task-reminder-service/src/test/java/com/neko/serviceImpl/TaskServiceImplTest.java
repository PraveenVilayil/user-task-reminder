package com.neko.serviceImpl;

import com.neko.TestFixtures;
import com.neko.config.ModelMapperConfig;
import com.neko.dto.PageResponse;
import com.neko.dto.TaskDto;
import com.neko.dto.TaskSearchCriteria;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Priority;
import com.neko.enums.Status;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.repositories.TaskRepository;
import com.neko.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    private TaskServiceImpl taskService;
    private User owner;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl(taskRepository, userRepository,
                new ModelMapperConfig().modelMapper(), TestFixtures.fixedClock());
        owner = TestFixtures.user();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void assignsAnIdAndStampsTheCreationTimeFromTheClock() {
            when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TaskDto request = new TaskDto();
            request.setName("Write the migration");
            request.setCreatedById(owner.getId());

            TaskDto created = taskService.create(request);

            assertThat(created.getId()).isNotNull();
            assertThat(created.getCreatedDate()).isEqualTo(TestFixtures.NOW);
            assertThat(created.getCreatedById()).isEqualTo(owner.getId());
        }

        @Test
        void defaultsStatusToPendingWhenNoneIsSupplied() {
            when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TaskDto request = new TaskDto();
            request.setName("Write the migration");
            request.setCreatedById(owner.getId());

            assertThat(taskService.create(request).getStatus()).isEqualTo(Status.PENDING);
        }

        @Test
        void refusesAnOwnerThatDoesNotExist() {
            UUID unknownOwner = UUID.randomUUID();
            when(userRepository.findById(unknownOwner)).thenReturn(Optional.empty());

            TaskDto request = new TaskDto();
            request.setName("Write the migration");
            request.setCreatedById(unknownOwner);

            assertThatThrownBy(() -> taskService.create(request))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
            verify(taskRepository, never()).save(any());
        }

        @Test
        void doesNotLetTheClientChooseTheIdentifier() {
            when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UUID attackerSuppliedId = UUID.randomUUID();
            TaskDto request = new TaskDto();
            request.setId(attackerSuppliedId);
            request.setName("Write the migration");
            request.setCreatedById(owner.getId());

            assertThat(taskService.create(request).getId()).isNotEqualTo(attackerSuppliedId);
        }
    }

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        void returnsTheTaskAsADto() {
            Task task = TestFixtures.task(UUID.randomUUID(), owner);
            when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

            TaskDto found = taskService.get(task.getId());

            assertThat(found.getName()).isEqualTo(task.getName());
            assertThat(found.getCreatedBy().getUserName()).isEqualTo(owner.getUserName());
            assertThat(found.getLabels()).containsExactly("release");
        }

        @Test
        void raisesTaskNotFoundForAnUnknownId() {
            UUID unknownId = UUID.randomUUID();
            when(taskRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.get(unknownId))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.TASK_NOT_FOUND);
        }

        @Test
        void marksATaskOverdueWhenItsDueDateHasPassed() {
            Task task = TestFixtures.task(UUID.randomUUID(), owner);
            task.setDueDate(LocalDateTime.now().minusDays(2));
            when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

            assertThat(taskService.get(task.getId()).getOverdue()).isTrue();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void appliesOnlyTheFieldsPresentOnTheRequest() {
            Task existing = TestFixtures.task(UUID.randomUUID(), owner);
            when(taskRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TaskDto patch = new TaskDto();
            patch.setPriority(Priority.CRITICAL);

            TaskDto updated = taskService.update(existing.getId(), patch);

            assertThat(updated.getPriority()).isEqualTo(Priority.CRITICAL);
            assertThat(updated.getName()).isEqualTo("Ship the release notes");
            assertThat(updated.getDescription()).isEqualTo("Summarise what changed in v1");
            assertThat(updated.getModifiedDate()).isEqualTo(TestFixtures.NOW);
        }

        @Test
        void raisesTaskNotFoundForAnUnknownId() {
            UUID unknownId = UUID.randomUUID();
            when(taskRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.update(unknownId, new TaskDto()))
                    .isInstanceOf(UserTaskReminderException.class);
        }

        @Test
        void clearsTheOverdueMarkerWhenTheDueDateMoves() {
            Task existing = TestFixtures.task(UUID.randomUUID(), owner);
            existing.setOverdueNotifiedAt(TestFixtures.NOW.minusDays(1));
            when(taskRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TaskDto patch = new TaskDto();
            patch.setDueDate(TestFixtures.NOW.plusDays(5));
            taskService.update(existing.getId(), patch);

            ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
            verify(taskRepository).save(saved.capture());
            assertThat(saved.getValue().getOverdueNotifiedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("task dependencies")
    class Dependencies {

        @Test
        void refusesCompletionWhileAPrerequisiteIsStillOpen() {
            Task blocker = TestFixtures.task(UUID.randomUUID(), owner);
            blocker.setStatus(Status.IN_PROGRESS);
            Task dependent = TestFixtures.task(UUID.randomUUID(), owner);
            dependent.setPrerequisites(List.of(blocker));

            when(taskRepository.findById(dependent.getId())).thenReturn(Optional.of(dependent));

            TaskDto patch = new TaskDto();
            patch.setStatus(Status.COMPLETED);

            assertThatThrownBy(() -> taskService.update(dependent.getId(), patch))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.TASK_PREREQUISITES_INCOMPLETE);
        }

        @Test
        void allowsCompletionOnceEveryPrerequisiteIsDone() {
            Task blocker = TestFixtures.task(UUID.randomUUID(), owner);
            blocker.setStatus(Status.COMPLETED);
            Task dependent = TestFixtures.task(UUID.randomUUID(), owner);
            dependent.setPrerequisites(List.of(blocker));

            when(taskRepository.findById(dependent.getId())).thenReturn(Optional.of(dependent));
            when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TaskDto patch = new TaskDto();
            patch.setStatus(Status.COMPLETED);

            assertThat(taskService.update(dependent.getId(), patch).getStatus()).isEqualTo(Status.COMPLETED);
        }

        @Test
        void refusesATaskThatWouldDependOnItself() {
            Task existing = TestFixtures.task(UUID.randomUUID(), owner);
            when(taskRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

            TaskDto patch = new TaskDto();
            patch.setPrerequisiteIds(List.of(existing.getId()));

            assertThatThrownBy(() -> taskService.update(existing.getId(), patch))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.TASK_PREREQUISITE_CYCLE);
        }

        @Test
        void refusesAnIndirectCycle() {
            Task middle = TestFixtures.task(UUID.randomUUID(), owner);
            Task existing = TestFixtures.task(UUID.randomUUID(), owner);
            // middle already depends on existing, so existing may not depend on middle.
            middle.setPrerequisites(List.of(existing));

            when(taskRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
            when(taskRepository.findById(middle.getId())).thenReturn(Optional.of(middle));

            TaskDto patch = new TaskDto();
            patch.setPrerequisiteIds(List.of(middle.getId()));

            assertThatThrownBy(() -> taskService.update(existing.getId(), patch))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.TASK_PREREQUISITE_CYCLE);
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        void wrapsTheRepositoryPageInTheResponseEnvelope() {
            Task task = TestFixtures.task(UUID.randomUUID(), owner);
            when(taskRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 20), 1));

            PageResponse<TaskDto> page = taskService.search(
                    TaskSearchCriteria.builder().status(Status.PENDING).build(),
                    PageRequest.of(0, 20, Sort.by("dueDate")));

            assertThat(page.content()).hasSize(1);
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.totalPages()).isEqualTo(1);
            assertThat(page.first()).isTrue();
            assertThat(page.last()).isTrue();
        }

        @Test
        void rejectsASortPropertyThatIsNotOnTheAllowList() {
            assertThatThrownBy(() -> taskService.search(null, PageRequest.of(0, 20, Sort.by("createdBy.password"))))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_SORT_PROPERTY);
            verify(taskRepository, never()).findAll(any(Specification.class), any(PageRequest.class));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void removesAnExistingTask() {
            Task task = TestFixtures.task(UUID.randomUUID(), owner);
            when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

            taskService.delete(task.getId());

            verify(taskRepository).delete(task);
        }

        @Test
        void raisesTaskNotFoundForAnUnknownId() {
            UUID unknownId = UUID.randomUUID();
            when(taskRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.delete(unknownId))
                    .isInstanceOf(UserTaskReminderException.class);
            verify(taskRepository, never()).delete(any(Task.class));
        }
    }
}
