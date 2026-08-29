package com.neko.repositories;

import com.neko.entity.Notification;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import com.neko.enums.Priority;
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

/**
 * Exercises the entity relationships against the real Flyway baseline on H2, so
 * a mapping that drifts from the migration fails the build.
 */
@DataJpaTest
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(newUser("owner-" + UUID.randomUUID()));
    }

    private User newUser(String userName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUserName(userName);
        user.setEmail(userName + "@example.com");
        user.setCreatedDate(LocalDateTime.now());
        user.setUpdatedDate(LocalDateTime.now());
        user.setRoles(new ArrayList<>(List.of("ROLE_USER")));
        return user;
    }

    private Task newTask(String name, Status status, LocalDateTime dueDate) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setName(name);
        task.setDescription(name + " description");
        task.setCreatedDate(LocalDateTime.now());
        task.setCreatedBy(owner);
        task.setDueDate(dueDate);
        task.setPriority(Priority.MEDIUM);
        task.setStatus(status);
        task.setLabels(new ArrayList<>(List.of("backend")));
        return task;
    }

    @Test
    void persistsATaskWithItsOwnerAndLabels() {
        Task saved = taskRepository.saveAndFlush(newTask("Ship it", Status.PENDING, LocalDateTime.now().plusDays(1)));
        entityManager.clear();

        Task reloaded = taskRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getCreatedBy().getId()).isEqualTo(owner.getId());
        assertThat(reloaded.getLabels()).containsExactly("backend");
        assertThat(reloaded.getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void storesUserRolesInTheirOwnCollectionTable() {
        entityManager.clear();

        User reloaded = userRepository.findById(owner.getId()).orElseThrow();

        assertThat(reloaded.getRoles()).containsExactly("ROLE_USER");
    }

    @Test
    void cascadesNotificationRemovalWhenATaskIsDeleted() {
        Task task = taskRepository.saveAndFlush(newTask("Ship it", Status.PENDING, LocalDateTime.now().plusDays(1)));

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setTask(task);
        notification.setUser(owner);
        notification.setMessage("heads up");
        notification.setSeen(false);
        notification.setCreatedDate(LocalDateTime.now());
        notification.setChannel(Channel.WEB);
        notification.setDeliveryStatus(DeliveryStatus.DELIVERED);
        entityManager.persistAndFlush(notification);
        UUID notificationId = notification.getId();
        entityManager.clear();

        // Reload so the task carries its notification collection, then remove it.
        taskRepository.delete(taskRepository.findById(task.getId()).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(Notification.class, notificationId)).isNull();
    }

    @Test
    void linksPrerequisiteTasksThroughTheJoinTable() {
        Task blocker = taskRepository.saveAndFlush(newTask("Blocker", Status.PENDING, null));
        Task dependent = newTask("Dependent", Status.PENDING, null);
        dependent.setPrerequisites(new ArrayList<>(List.of(blocker)));
        Task saved = taskRepository.saveAndFlush(dependent);
        entityManager.clear();

        Task reloaded = taskRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPrerequisites()).extracting(Task::getId).containsExactly(blocker.getId());
    }

    @Test
    void findOverdueReturnsOnlyLateUnfinishedUnflaggedTasks() {
        LocalDateTime now = LocalDateTime.now();
        Task late = taskRepository.saveAndFlush(newTask("Late", Status.IN_PROGRESS, now.minusDays(1)));
        taskRepository.saveAndFlush(newTask("Upcoming", Status.PENDING, now.plusDays(1)));
        taskRepository.saveAndFlush(newTask("Done", Status.COMPLETED, now.minusDays(1)));
        taskRepository.saveAndFlush(newTask("No due date", Status.PENDING, null));

        Task alreadyFlagged = newTask("Already announced", Status.PENDING, now.minusDays(2));
        alreadyFlagged.setOverdueNotifiedAt(now.minusHours(1));
        taskRepository.saveAndFlush(alreadyFlagged);
        entityManager.clear();

        List<Task> overdue = taskRepository.findOverdue(now, Status.COMPLETED);

        assertThat(overdue).extracting(Task::getId).containsExactly(late.getId());
    }

    @Test
    void findsTasksByOwner() {
        Task mine = taskRepository.saveAndFlush(newTask("Mine", Status.PENDING, null));
        User other = userRepository.saveAndFlush(newUser("other-" + UUID.randomUUID()));
        Task theirs = newTask("Theirs", Status.PENDING, null);
        theirs.setCreatedBy(other);
        taskRepository.saveAndFlush(theirs);
        entityManager.clear();

        assertThat(taskRepository.findByCreatedById(owner.getId()))
                .extracting(Task::getId)
                .containsExactly(mine.getId());
    }

    @Test
    void findAllFetchesTheOwnerEagerlyThroughTheEntityGraph() {
        taskRepository.saveAndFlush(newTask("Ship it", Status.PENDING, null));
        entityManager.clear();

        List<Task> tasks = taskRepository.findAll();

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getCreatedBy().getUserName()).isEqualTo(owner.getUserName());
    }
}
