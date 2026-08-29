package com.neko.repositories;

import com.neko.entity.Reminder;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Channel;
import com.neko.enums.Priority;
import com.neko.enums.ReminderStatus;
import com.neko.enums.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReminderRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 9, 0);

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Task task;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setUserName("owner-" + UUID.randomUUID());
        owner.setEmail(owner.getUserName() + "@example.com");
        owner.setRoles(new ArrayList<>());
        userRepository.saveAndFlush(owner);

        Task newTask = new Task();
        newTask.setId(UUID.randomUUID());
        newTask.setName("Ship it");
        newTask.setCreatedBy(owner);
        newTask.setCreatedDate(NOW);
        newTask.setPriority(Priority.HIGH);
        newTask.setStatus(Status.PENDING);
        newTask.setLabels(new ArrayList<>());
        task = taskRepository.saveAndFlush(newTask);
    }

    private Reminder reminder(String cron, LocalDateTime nextFireTime, ReminderStatus status) {
        Reminder reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setTask(task);
        reminder.setCron(cron);
        reminder.setMessage("ping");
        reminder.setChannel(Channel.WEB);
        reminder.setStatus(status);
        reminder.setNextFireTime(nextFireTime);
        reminder.setCreatedDate(NOW);
        return reminder;
    }

    @Test
    void findDueReturnsScheduledRemindersAtOrBeforeTheInstant() {
        Reminder overdue = reminderRepository.saveAndFlush(
                reminder("0 0 9 * * *", NOW.minusHours(2), ReminderStatus.SCHEDULED));
        Reminder exactlyNow = reminderRepository.saveAndFlush(
                reminder(null, NOW, ReminderStatus.SCHEDULED));
        reminderRepository.saveAndFlush(reminder(null, NOW.plusHours(1), ReminderStatus.SCHEDULED));
        reminderRepository.saveAndFlush(reminder(null, NOW.minusDays(1), ReminderStatus.CANCELLED));
        reminderRepository.saveAndFlush(reminder(null, null, ReminderStatus.SCHEDULED));
        entityManager.clear();

        List<Reminder> due = reminderRepository.findDue(ReminderStatus.SCHEDULED, NOW);

        assertThat(due).extracting(Reminder::getId)
                .containsExactly(overdue.getId(), exactlyNow.getId());
    }

    @Test
    void findDueFetchesTheTaskAndItsOwnerInOneQuery() {
        reminderRepository.saveAndFlush(reminder(null, NOW.minusMinutes(1), ReminderStatus.SCHEDULED));
        entityManager.clear();

        Reminder due = reminderRepository.findDue(ReminderStatus.SCHEDULED, NOW).get(0);

        assertThat(due.getTask().getName()).isEqualTo("Ship it");
        assertThat(due.getTask().getCreatedBy()).isNotNull();
    }

    @Test
    void findsRemindersForATaskOrderedByNextFireTime() {
        Reminder later = reminderRepository.saveAndFlush(
                reminder(null, NOW.plusDays(2), ReminderStatus.SCHEDULED));
        Reminder sooner = reminderRepository.saveAndFlush(
                reminder(null, NOW.plusHours(1), ReminderStatus.SCHEDULED));
        entityManager.clear();

        assertThat(reminderRepository.findByTaskIdOrderByNextFireTimeAsc(task.getId()))
                .extracting(Reminder::getId)
                .containsExactly(sooner.getId(), later.getId());
    }

    @Test
    void deletingATaskRemovesItsReminders() {
        UUID reminderId = reminderRepository.saveAndFlush(
                reminder(null, NOW.plusDays(1), ReminderStatus.SCHEDULED)).getId();
        entityManager.clear();

        taskRepository.delete(taskRepository.findById(task.getId()).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(reminderRepository.findById(reminderId)).isEmpty();
    }
}
