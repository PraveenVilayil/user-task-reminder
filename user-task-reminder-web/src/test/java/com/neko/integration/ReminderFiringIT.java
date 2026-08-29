package com.neko.integration;

import com.neko.dto.NotificationDto;
import com.neko.dto.ReminderDto;
import com.neko.dto.TaskDto;
import com.neko.dto.UserDto;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import com.neko.enums.ReminderStatus;
import com.neko.repositories.ReminderRepository;
import com.neko.scheduling.OverdueTaskSweeper;
import com.neko.service.NotificationService;
import com.neko.service.ReminderService;
import com.neko.service.TaskService;
import com.neko.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The whole reminder chain end to end: schedule a reminder, move the clock past
 * its fire time, sweep, and check that a real notification came out the far end
 * and was dispatched over its channel.
 *
 * <p>Time is controlled by replacing the application Clock, so nothing here
 * sleeps or races.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReminderFiringIT {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 3, 2, 9, 0);

    @MockitoBean
    private Clock clock;

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private OverdueTaskSweeper overdueTaskSweeper;

    @Autowired
    private ReminderRepository reminderRepository;

    private UUID ownerId;
    private UUID taskId;

    private void moveClockTo(LocalDateTime instant) {
        when(clock.instant()).thenReturn(instant.toInstant(ZoneOffset.UTC));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        // fireDueReminders sweeps every scheduled reminder in the database, so
        // each test starts from an empty reminder table to keep its counts exact.
        reminderRepository.deleteAll();
        moveClockTo(BASE);

        UserDto owner = new UserDto();
        owner.setUserName("fire-" + UUID.randomUUID());
        owner.setEmail(owner.getUserName() + "@example.com");
        ownerId = userService.create(owner).getId();

        TaskDto task = new TaskDto();
        task.setName("Ship the release notes");
        task.setCreatedById(ownerId);
        taskId = taskService.create(task).getId();
    }

    private List<NotificationDto> notificationsForOwner() {
        return notificationService.getByUser(ownerId);
    }

    @Test
    void aOneTimeReminderFiresOnceAndProducesADeliveredNotification() {
        ReminderDto request = new ReminderDto();
        request.setTaskId(taskId);
        request.setDueDate(BASE.plusHours(1));
        request.setMessage("Release window opens now");
        ReminderDto scheduled = reminderService.create(request);

        assertThat(scheduled.getNextFireTime()).isEqualTo(BASE.plusHours(1));
        assertThat(reminderService.fireDueReminders()).isZero();

        moveClockTo(BASE.plusHours(2));
        assertThat(reminderService.fireDueReminders()).isEqualTo(1);

        List<NotificationDto> notifications = notificationsForOwner();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getMessage()).isEqualTo("Release window opens now");
        assertThat(notifications.get(0).getTaskId()).isEqualTo(taskId);
        assertThat(notifications.get(0).getChannel()).isEqualTo(Channel.WEB);
        assertThat(notifications.get(0).getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);

        ReminderDto afterFiring = reminderService.get(scheduled.getId());
        assertThat(afterFiring.getStatus()).isEqualTo(ReminderStatus.FIRED);
        assertThat(afterFiring.getNextFireTime()).isNull();
        assertThat(afterFiring.getFireCount()).isEqualTo(1);

        // A second sweep at the same instant must not fire it again.
        assertThat(reminderService.fireDueReminders()).isZero();
        assertThat(notificationsForOwner()).hasSize(1);
    }

    @Test
    void aRecurringReminderFiresOncePerOccurrenceAndReschedulesItself() {
        ReminderDto request = new ReminderDto();
        request.setTaskId(taskId);
        request.setCron("0 0 12 * * *");
        request.setMessage("Midday check-in");
        ReminderDto scheduled = reminderService.create(request);

        assertThat(scheduled.getNextFireTime()).isEqualTo(BASE.withHour(12));

        moveClockTo(BASE.withHour(12).plusMinutes(1));
        assertThat(reminderService.fireDueReminders()).isEqualTo(1);

        ReminderDto afterFirstFire = reminderService.get(scheduled.getId());
        assertThat(afterFirstFire.getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
        assertThat(afterFirstFire.getNextFireTime()).isEqualTo(BASE.plusDays(1).withHour(12));
        assertThat(notificationsForOwner()).hasSize(1);

        moveClockTo(BASE.plusDays(1).withHour(12).plusMinutes(1));
        assertThat(reminderService.fireDueReminders()).isEqualTo(1);
        assertThat(notificationsForOwner()).hasSize(2);
        assertThat(reminderService.get(scheduled.getId()).getFireCount()).isEqualTo(2);
    }

    @Test
    void aCancelledReminderNeverFires() {
        ReminderDto request = new ReminderDto();
        request.setTaskId(taskId);
        request.setDueDate(BASE.plusHours(1));
        ReminderDto scheduled = reminderService.create(request);

        reminderService.cancel(scheduled.getId());
        moveClockTo(BASE.plusDays(1));

        assertThat(reminderService.fireDueReminders()).isZero();
        assertThat(notificationsForOwner()).isEmpty();
    }

    @Test
    void startupRecoveryFiresARecurringReminderOnceAfterALongOutage() {
        ReminderDto request = new ReminderDto();
        request.setTaskId(taskId);
        request.setCron("0 0 12 * * *");
        ReminderDto scheduled = reminderService.create(request);

        // Five days of midday occurrences elapsed while the application was down.
        moveClockTo(BASE.plusDays(5).withHour(13));

        assertThat(reminderService.recoverMissedReminders()).isEqualTo(1);

        assertThat(notificationsForOwner()).hasSize(1);
        assertThat(reminderService.get(scheduled.getId()).getFireCount()).isEqualTo(1);
        assertThat(reminderService.get(scheduled.getId()).getNextFireTime())
                .isEqualTo(BASE.plusDays(6).withHour(12));
    }

    @Test
    void theOverdueSweepAnnouncesALateTaskExactlyOnce() {
        TaskDto patch = new TaskDto();
        patch.setDueDate(BASE.plusHours(1));
        taskService.update(taskId, patch);

        moveClockTo(BASE.plusDays(1));

        overdueTaskSweeper.sweep();

        assertThat(notificationsForOwner()).hasSize(1);
        assertThat(notificationsForOwner().get(0).getMessage()).contains("Task is overdue");

        // The marker on the task stops it being announced again.
        overdueTaskSweeper.sweep();
        assertThat(notificationsForOwner()).hasSize(1);
    }
}
