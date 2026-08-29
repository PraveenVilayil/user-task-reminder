package com.neko.scheduling;

import com.neko.entity.Task;
import com.neko.enums.Status;
import com.neko.repositories.TaskRepository;
import com.neko.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Flags tasks that have slipped past their due date and raises one notification
 * for each.
 *
 * <p>{@code overdueNotifiedAt} on the task is the idempotency marker, so a task
 * is announced once and not on every sweep. Moving the due date clears it, which
 * lets a rescheduled task be announced again if it slips a second time.</p>
 */
@Component
public class OverdueTaskSweeper {

    private static final Logger log = LoggerFactory.getLogger(OverdueTaskSweeper.class);

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public OverdueTaskSweeper(TaskRepository taskRepository, NotificationService notificationService, Clock clock) {
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * @return the number of tasks newly flagged overdue
     */
    @Transactional
    public int sweep() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Task> overdue = taskRepository.findOverdue(now, Status.COMPLETED);
        if (overdue.isEmpty()) {
            return 0;
        }

        for (Task task : overdue) {
            notificationService.raise(
                    task.getId(),
                    task.getCreatedBy() == null ? null : task.getCreatedBy().getId(),
                    "Task is overdue: " + task.getName() + " (due " + task.getDueDate() + ")",
                    null);
            task.setOverdueNotifiedAt(now);
        }
        taskRepository.saveAll(overdue);
        log.info("Flagged {} overdue task(s)", overdue.size());
        return overdue.size();
    }
}
