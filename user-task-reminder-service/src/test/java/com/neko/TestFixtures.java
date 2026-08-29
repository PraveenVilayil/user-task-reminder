package com.neko;

import com.neko.entity.Notification;
import com.neko.entity.Reminder;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import com.neko.enums.Priority;
import com.neko.enums.ReminderStatus;
import com.neko.enums.Status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Shared builders for the service unit tests. Everything is anchored to
 * {@link #NOW} so assertions can compare exact instants.
 */
public final class TestFixtures {

    public static final ZoneId ZONE = ZoneOffset.UTC;
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 9, 0);

    private TestFixtures() {
    }

    /** A clock frozen at {@link #NOW}. */
    public static Clock fixedClock() {
        return fixedClockAt(NOW);
    }

    public static Clock fixedClockAt(LocalDateTime instant) {
        return Clock.fixed(instant.toInstant(ZoneOffset.UTC), ZONE);
    }

    public static User user() {
        return user(UUID.randomUUID(), "vivek");
    }

    public static User user(UUID id, String userName) {
        User user = new User();
        user.setId(id);
        user.setUserName(userName);
        user.setEmail(userName + "@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setCreatedDate(NOW);
        user.setUpdatedDate(NOW);
        user.setRoles(new ArrayList<>(java.util.List.of("ROLE_USER")));
        return user;
    }

    public static Task task() {
        return task(UUID.randomUUID(), user());
    }

    public static Task task(UUID id, User owner) {
        Task task = new Task();
        task.setId(id);
        task.setName("Ship the release notes");
        task.setDescription("Summarise what changed in v1");
        task.setCreatedDate(NOW);
        task.setCreatedBy(owner);
        task.setDueDate(NOW.plusDays(1));
        task.setPriority(Priority.HIGH);
        task.setStatus(Status.PENDING);
        task.setLabels(new ArrayList<>(java.util.List.of("release")));
        return task;
    }

    public static Reminder oneTimeReminder(Task task, LocalDateTime fireAt) {
        Reminder reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setTask(task);
        reminder.setDueDate(fireAt);
        reminder.setNextFireTime(fireAt);
        reminder.setChannel(Channel.WEB);
        reminder.setStatus(ReminderStatus.SCHEDULED);
        reminder.setCreatedDate(NOW);
        return reminder;
    }

    public static Reminder recurringReminder(Task task, String cron, LocalDateTime nextFireTime) {
        Reminder reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setTask(task);
        reminder.setCron(cron);
        reminder.setNextFireTime(nextFireTime);
        reminder.setChannel(Channel.WEB);
        reminder.setStatus(ReminderStatus.SCHEDULED);
        reminder.setCreatedDate(NOW);
        return reminder;
    }

    public static Notification notification(User user, Task task) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUser(user);
        notification.setTask(task);
        notification.setMessage("Something happened");
        notification.setSeen(false);
        notification.setCreatedDate(NOW);
        notification.setChannel(Channel.WEB);
        notification.setDeliveryStatus(DeliveryStatus.PENDING);
        return notification;
    }
}
