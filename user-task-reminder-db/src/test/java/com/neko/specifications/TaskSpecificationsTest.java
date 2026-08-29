package com.neko.specifications;

import com.neko.dto.TaskSearchCriteria;
import com.neko.entity.Task;
import com.neko.entity.User;
import com.neko.enums.Priority;
import com.neko.enums.Status;
import com.neko.repositories.TaskRepository;
import com.neko.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The search predicates are executed against H2 rather than asserted structurally,
 * so a filter that does not translate to valid SQL fails here.
 */
@DataJpaTest
class TaskSpecificationsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 9, 0);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = saveUser("alice");
        bob = saveUser("bob");

        saveTask("Write release notes", "Summarise the v1 changes", alice,
                Status.PENDING, Priority.HIGH, NOW.plusDays(1), List.of("docs", "release"));
        saveTask("Fix login bug", "Users cannot sign in", bob,
                Status.IN_PROGRESS, Priority.CRITICAL, NOW.minusDays(1), List.of("bug"));
        saveTask("Archive old releases", "Housekeeping", alice,
                Status.COMPLETED, Priority.LOW, NOW.minusDays(5), List.of("release"));
        entityManager.clear();
    }

    private User saveUser(String userName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUserName(userName + "-" + UUID.randomUUID());
        user.setEmail(user.getUserName() + "@example.com");
        user.setRoles(new ArrayList<>());
        return userRepository.saveAndFlush(user);
    }

    private void saveTask(String name, String description, User owner, Status status,
                          Priority priority, LocalDateTime dueDate, List<String> labels) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setName(name);
        task.setDescription(description);
        task.setCreatedBy(owner);
        task.setCreatedDate(NOW);
        task.setStatus(status);
        task.setPriority(priority);
        task.setDueDate(dueDate);
        task.setLabels(new ArrayList<>(labels));
        taskRepository.saveAndFlush(task);
    }

    private List<String> search(TaskSearchCriteria criteria) {
        return taskRepository.findAll(TaskSpecifications.from(criteria, NOW),
                        PageRequest.of(0, 20, Sort.by("name")))
                .getContent().stream()
                .map(Task::getName)
                .toList();
    }

    @Test
    void anEmptyCriteriaMatchesEverything() {
        assertThat(search(TaskSearchCriteria.builder().build())).hasSize(3);
    }

    @Test
    void nullCriteriaMatchesEverything() {
        assertThat(search(null)).hasSize(3);
    }

    @Test
    void filtersByStatus() {
        assertThat(search(TaskSearchCriteria.builder().status(Status.IN_PROGRESS).build()))
                .containsExactly("Fix login bug");
    }

    @Test
    void filtersByPriority() {
        assertThat(search(TaskSearchCriteria.builder().priority(Priority.LOW).build()))
                .containsExactly("Archive old releases");
    }

    @Test
    void filtersByAssignee() {
        assertThat(search(TaskSearchCriteria.builder().assigneeId(alice.getId()).build()))
                .containsExactly("Archive old releases", "Write release notes");
    }

    @Test
    void filtersByDueDateRange() {
        assertThat(search(TaskSearchCriteria.builder()
                .dueFrom(NOW.minusDays(2))
                .dueTo(NOW.plusDays(2))
                .build()))
                .containsExactly("Fix login bug", "Write release notes");
    }

    @Test
    void matchesFreeTextAcrossNameAndDescriptionCaseInsensitively() {
        assertThat(search(TaskSearchCriteria.builder().query("RELEASE").build()))
                .containsExactly("Archive old releases", "Write release notes");

        // "sign in" only appears in a description.
        assertThat(search(TaskSearchCriteria.builder().query("sign in").build()))
                .containsExactly("Fix login bug");
    }

    @Test
    void filtersByLabel() {
        assertThat(search(TaskSearchCriteria.builder().label("release").build()))
                .containsExactly("Archive old releases", "Write release notes");
    }

    @Test
    void filtersOverdueTasks() {
        // "Archive old releases" is late but COMPLETED, so it is not overdue.
        assertThat(search(TaskSearchCriteria.builder().overdue(true).build()))
                .containsExactly("Fix login bug");

        assertThat(search(TaskSearchCriteria.builder().overdue(false).build()))
                .containsExactly("Archive old releases", "Write release notes");
    }

    @Test
    void combinesFiltersConjunctively() {
        assertThat(search(TaskSearchCriteria.builder()
                .assigneeId(alice.getId())
                .query("release")
                .status(Status.PENDING)
                .build()))
                .containsExactly("Write release notes");
    }

    @Test
    void appliesSortingAndPaging() {
        var page = taskRepository.findAll(
                TaskSpecifications.from(TaskSearchCriteria.builder().build(), NOW),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "name")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Task::getName)
                .containsExactly("Write release notes", "Fix login bug");
    }
}
