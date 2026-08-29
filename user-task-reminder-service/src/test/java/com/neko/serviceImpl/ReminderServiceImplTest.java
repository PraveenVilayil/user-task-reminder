package com.neko.serviceImpl;

import com.neko.TestFixtures;
import com.neko.config.ModelMapperConfig;
import com.neko.dto.NotificationDto;
import com.neko.dto.ReminderDto;
import com.neko.entity.Reminder;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Channel;
import com.neko.enums.ReminderStatus;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.notification.NotificationProperties;
import com.neko.repositories.ReminderRepository;
import com.neko.repositories.TaskRepository;
import com.neko.scheduling.ReminderScheduleCalculator;
import com.neko.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reminder scheduling is driven by an injected clock, so every assertion here is
 * about exact instants rather than about waiting for a timer.
 */
@ExtendWith(MockitoExtension.class)
class ReminderServiceImplTest {

    @Mock
    private ReminderRepository reminderRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationService notificationService;

    private ReminderServiceImpl reminderService;
    private User owner;
    private Task task;

    @BeforeEach
    void setUp() {
        owner = TestFixtures.user();
        task = TestFixtures.task(UUID.randomUUID(), owner);
        reminderService = buildServiceAt(TestFixtures.NOW);
    }

    private ReminderServiceImpl buildServiceAt(LocalDateTime instant) {
        return new ReminderServiceImpl(reminderRepository, taskRepository, notificationService,
                new ReminderScheduleCalculator(), new NotificationProperties(),
                new ModelMapperConfig().modelMapper(), TestFixtures.fixedClockAt(instant));
    }

    @Nested
    @DisplayName("scheduling a reminder")
    class Scheduling {

        @Test
        void aOneTimeReminderFiresAtItsDueDate() {
            LocalDateTime fireAt = TestFixtures.NOW.plusHours(3);
            when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
            when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));

            ReminderDto request = new ReminderDto();
            request.setTaskId(task.getId());
            request.setDueDate(fireAt);

            ReminderDto created = reminderService.create(request);

            assertThat(created.getNextFireTime()).isEqualTo(fireAt);
            assertThat(created.getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
            assertThat(created.getCron()).isNull();
            assertThat(created.getChannel()).isEqualTo(Channel.WEB);
        }

        @Test
        void aRecurringReminderFiresAtTheNextCronOccurrence() {
            when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
            when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));

            ReminderDto request = new ReminderDto();
            request.setTaskId(task.getId());
            request.setCron("0 0 17 * * *");

            ReminderDto created = reminderService.create(request);

            // The clock is at 09:00, so the next 17:00 is the same day.
            assertThat(created.getNextFireTime()).isEqualTo(TestFixtures.NOW.withHour(17).withMinute(0));
        }

        @Test
        void refusesAReminderWithBothACronAndADueDate() {
            ReminderDto request = new ReminderDto();
            request.setTaskId(task.getId());
            request.setCron("0 0 17 * * *");
            request.setDueDate(TestFixtures.NOW.plusHours(1));

            assertThatThrownBy(() -> reminderService.create(request))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REMINDER_SCHEDULE);
            verify(reminderRepository, never()).save(any());
        }

        @Test
        void refusesAReminderWithNeitherACronNorADueDate() {
            ReminderDto request = new ReminderDto();
            request.setTaskId(task.getId());

            assertThatThrownBy(() -> reminderService.create(request))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_REMINDER_SCHEDULE);
        }

        @Test
        void refusesAOneTimeReminderScheduledInThePast() {
            when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

            ReminderDto request = new ReminderDto();
            request.setTaskId(task.getId());
            request.setDueDate(TestFixtures.NOW.minusMinutes(1));

            assertThatThrownBy(() -> reminderService.create(request))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.REMINDER_DUE_DATE_IN_PAST);
        }

        @Test
        void refusesAnUnparseableCronExpression() {
            when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

            ReminderDto request = new ReminderDto();
            request.setTaskId(task.getId());
            request.setCron("every other tuesday");

            assertThatThrownBy(() -> reminderService.create(request))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CRON_EXPRESSION);
        }

        @Test
        void refusesAReminderForATaskThatDoesNotExist() {
            UUID unknownTask = UUID.randomUUID();
            when(taskRepository.findById(unknownTask)).thenReturn(Optional.empty());

            ReminderDto request = new ReminderDto();
            request.setTaskId(unknownTask);
            request.setDueDate(TestFixtures.NOW.plusHours(1));

            assertThatThrownBy(() -> reminderService.create(request))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.TASK_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("firing due reminders")
    class Firing {

        @Test
        void raisesANotificationAndTerminatesAOneTimeReminder() {
            Reminder reminder = TestFixtures.oneTimeReminder(task, TestFixtures.NOW);
            reminder.setMessage("Stand-up now");
            when(reminderRepository.findDue(ReminderStatus.SCHEDULED, TestFixtures.NOW))
                    .thenReturn(List.of(reminder));

            assertThat(reminderService.fireDueReminders()).isEqualTo(1);

            verify(notificationService).raise(task.getId(), owner.getId(), "Stand-up now", Channel.WEB);
            assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FIRED);
            assertThat(reminder.getNextFireTime()).isNull();
            assertThat(reminder.getLastFiredAt()).isEqualTo(TestFixtures.NOW);
            assertThat(reminder.getFireCount()).isEqualTo(1);
            verify(reminderRepository).saveAll(List.of(reminder));
        }

        @Test
        void reschedulesARecurringReminderAndKeepsItActive() {
            Reminder reminder = TestFixtures.recurringReminder(task, "0 0 9 * * *", TestFixtures.NOW);
            when(reminderRepository.findDue(ReminderStatus.SCHEDULED, TestFixtures.NOW))
                    .thenReturn(List.of(reminder));

            reminderService.fireDueReminders();

            assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
            assertThat(reminder.getNextFireTime()).isEqualTo(TestFixtures.NOW.plusDays(1));
            assertThat(reminder.getFireCount()).isEqualTo(1);
        }

        @Test
        void fallsBackToAGeneratedMessageWhenTheReminderHasNone() {
            Reminder reminder = TestFixtures.oneTimeReminder(task, TestFixtures.NOW);
            when(reminderRepository.findDue(ReminderStatus.SCHEDULED, TestFixtures.NOW))
                    .thenReturn(List.of(reminder));

            reminderService.fireDueReminders();

            verify(notificationService).raise(eq(task.getId()), eq(owner.getId()),
                    eq("Reminder: Ship the release notes"), eq(Channel.WEB));
        }

        @Test
        void toleratesATaskWithNoOwner() {
            Task orphan = TestFixtures.task(UUID.randomUUID(), null);
            Reminder reminder = TestFixtures.oneTimeReminder(orphan, TestFixtures.NOW);
            when(reminderRepository.findDue(ReminderStatus.SCHEDULED, TestFixtures.NOW))
                    .thenReturn(List.of(reminder));

            reminderService.fireDueReminders();

            verify(notificationService).raise(eq(orphan.getId()), isNull(), any(), eq(Channel.WEB));
        }

        @Test
        void doesNothingWhenNothingIsDue() {
            when(reminderRepository.findDue(ReminderStatus.SCHEDULED, TestFixtures.NOW)).thenReturn(List.of());

            assertThat(reminderService.fireDueReminders()).isZero();

            verify(reminderRepository, never()).saveAll(any());
            verify(notificationService, never()).raise(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("recovering reminders missed during downtime")
    class Recovery {

        @Test
        void firesAMissedOneTimeReminderOnceAndClosesIt() {
            Reminder reminder = TestFixtures.oneTimeReminder(task, TestFixtures.NOW.minusDays(3));
            when(reminderRepository.findDue(ReminderStatus.SCHEDULED, TestFixtures.NOW))
                    .thenReturn(List.of(reminder));

            assertThat(reminderService.recoverMissedReminders()).isEqualTo(1);

            verify(notificationService).raise(any(), any(), any(), any());
            assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FIRED);
            assertThat(reminder.getFireCount()).isEqualTo(1);
        }

        @Test
        void firesARecurringReminderOnceAndSkipsTheOccurrencesItSleptThrough() {
            // Scheduled for 09:00 four days ago; the application has been down since.
            Reminder reminder = TestFixtures.recurringReminder(
                    task, "0 0 9 * * *", TestFixtures.NOW.minusDays(4));
            when(reminderRepository.findDue(ReminderStatus.SCHEDULED, TestFixtures.NOW))
                    .thenReturn(List.of(reminder));

            reminderService.recoverMissedReminders();

            // One notification, not four.
            verify(notificationService, org.mockito.Mockito.times(1)).raise(any(), any(), any(), any());
            assertThat(reminder.getFireCount()).isEqualTo(1);
            assertThat(reminder.getNextFireTime()).isEqualTo(TestFixtures.NOW.plusDays(1));
            assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
        }

        @Test
        void reportsZeroWhenNothingWasMissed() {
            when(reminderRepository.findDue(ReminderStatus.SCHEDULED, TestFixtures.NOW)).thenReturn(List.of());

            assertThat(reminderService.recoverMissedReminders()).isZero();
        }
    }

    @Nested
    @DisplayName("maintenance")
    class Maintenance {

        @Test
        void cancellingStopsFutureFiringWithoutDeletingTheRow() {
            Reminder reminder = TestFixtures.oneTimeReminder(task, TestFixtures.NOW.plusDays(1));
            when(reminderRepository.findById(reminder.getId())).thenReturn(Optional.of(reminder));
            when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));

            ReminderDto cancelled = reminderService.cancel(reminder.getId());

            assertThat(cancelled.getStatus()).isEqualTo(ReminderStatus.CANCELLED);
            assertThat(cancelled.getNextFireTime()).isNull();
            verify(reminderRepository, never()).delete(any());
        }

        @Test
        void changingTheCronRecomputesTheNextFireTimeImmediately() {
            Reminder reminder = TestFixtures.recurringReminder(task, "0 0 9 * * *", TestFixtures.NOW.plusDays(1));
            when(reminderRepository.findById(reminder.getId())).thenReturn(Optional.of(reminder));
            when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));

            ReminderDto patch = new ReminderDto();
            patch.setCron("0 30 9 * * *");

            ReminderDto updated = reminderService.update(reminder.getId(), patch);

            assertThat(updated.getCron()).isEqualTo("0 30 9 * * *");
            assertThat(updated.getNextFireTime()).isEqualTo(TestFixtures.NOW.withHour(9).withMinute(30));
        }

        @Test
        void updatingRejectsAnUnparseableCron() {
            Reminder reminder = TestFixtures.recurringReminder(task, "0 0 9 * * *", TestFixtures.NOW.plusDays(1));
            when(reminderRepository.findById(reminder.getId())).thenReturn(Optional.of(reminder));

            ReminderDto patch = new ReminderDto();
            patch.setCron("nope");

            assertThatThrownBy(() -> reminderService.update(reminder.getId(), patch))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CRON_EXPRESSION);
        }

        @Test
        void getRaisesReminderNotFoundForAnUnknownId() {
            UUID unknownId = UUID.randomUUID();
            when(reminderRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reminderService.get(unknownId))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.REMINDER_NOT_FOUND);
        }

        @Test
        void listingByTaskRejectsAnUnknownTask() {
            UUID unknownTask = UUID.randomUUID();
            when(taskRepository.existsById(unknownTask)).thenReturn(false);

            assertThatThrownBy(() -> reminderService.listByTask(unknownTask))
                    .isInstanceOf(UserTaskReminderException.class)
                    .extracting(exception -> ((UserTaskReminderException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.TASK_NOT_FOUND);
        }
    }
}
