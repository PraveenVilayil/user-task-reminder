package com.neko.scheduling;

import com.neko.TestFixtures;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Status;
import com.neko.repositories.TaskRepository;
import com.neko.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverdueTaskSweeperTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationService notificationService;

    private OverdueTaskSweeper sweeper;
    private User owner;

    @BeforeEach
    void setUp() {
        sweeper = new OverdueTaskSweeper(taskRepository, notificationService, TestFixtures.fixedClock());
        owner = TestFixtures.user();
    }

    @Test
    void raisesOneNotificationPerOverdueTaskAndStampsTheMarker() {
        Task late = TestFixtures.task(UUID.randomUUID(), owner);
        late.setDueDate(TestFixtures.NOW.minusDays(1));
        when(taskRepository.findOverdue(TestFixtures.NOW, Status.COMPLETED)).thenReturn(List.of(late));

        assertThat(sweeper.sweep()).isEqualTo(1);

        verify(notificationService).raise(eq(late.getId()), eq(owner.getId()),
                contains("Task is overdue: Ship the release notes"), isNull());
        assertThat(late.getOverdueNotifiedAt()).isEqualTo(TestFixtures.NOW);
        verify(taskRepository).saveAll(List.of(late));
    }

    @Test
    void doesNothingWhenNoTaskIsOverdue() {
        when(taskRepository.findOverdue(TestFixtures.NOW, Status.COMPLETED)).thenReturn(List.of());

        assertThat(sweeper.sweep()).isZero();

        verify(notificationService, never()).raise(any(), any(), any(), any());
        verify(taskRepository, never()).saveAll(any());
    }

    @Test
    void toleratesAnOverdueTaskWithNoOwner() {
        Task orphan = TestFixtures.task(UUID.randomUUID(), null);
        orphan.setDueDate(TestFixtures.NOW.minusHours(2));
        when(taskRepository.findOverdue(TestFixtures.NOW, Status.COMPLETED)).thenReturn(List.of(orphan));

        assertThat(sweeper.sweep()).isEqualTo(1);

        verify(notificationService).raise(eq(orphan.getId()), isNull(), any(), isNull());
    }
}
