package com.neko.serviceImpl;

import com.neko.dto.ReminderDto;
import com.neko.entity.Reminder;
import com.neko.entity.Task;
import com.neko.enums.ReminderStatus;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import com.neko.notification.NotificationProperties;
import com.neko.repositories.ReminderRepository;
import com.neko.repositories.TaskRepository;
import com.neko.scheduling.ReminderScheduleCalculator;
import com.neko.service.NotificationService;
import com.neko.service.ReminderService;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the reminder lifecycle: creation, rescheduling, firing and cancellation.
 *
 * <p>The scheduler component only calls {@link #fireDueReminders()}; every
 * decision about <em>when</em> a reminder next fires lives here, driven by an
 * injected {@link Clock} so the behaviour is deterministic under test.</p>
 */
@Service
public class ReminderServiceImpl implements ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderServiceImpl.class);

    private final ReminderRepository reminderRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final ReminderScheduleCalculator calculator;
    private final NotificationProperties notificationProperties;
    private final ModelMapper mapper;
    private final Clock clock;

    public ReminderServiceImpl(ReminderRepository reminderRepository,
                               TaskRepository taskRepository,
                               NotificationService notificationService,
                               ReminderScheduleCalculator calculator,
                               NotificationProperties notificationProperties,
                               ModelMapper mapper,
                               Clock clock) {
        this.reminderRepository = reminderRepository;
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
        this.calculator = calculator;
        this.notificationProperties = notificationProperties;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReminderDto create(ReminderDto request) {
        LocalDateTime now = LocalDateTime.now(clock);
        assertScheduleShape(request);

        Reminder reminder = new Reminder();
        reminder.setId(UUID.randomUUID());
        reminder.setTask(resolveTask(request.getTaskId()));
        reminder.setCron(normalise(request.getCron()));
        reminder.setMessage(request.getMessage());
        reminder.setDueDate(request.getDueDate());
        reminder.setChannel(request.getChannel() == null
                ? notificationProperties.getDefaultChannel()
                : request.getChannel());
        reminder.setStatus(ReminderStatus.SCHEDULED);
        reminder.setCreatedDate(now);

        if (reminder.isRecurring()) {
            calculator.validate(reminder.getCron());
        } else if (reminder.getDueDate().isBefore(now)) {
            throw new UserTaskReminderException(ErrorCode.REMINDER_DUE_DATE_IN_PAST, reminder.getDueDate());
        }

        reminder.setNextFireTime(calculator.initialFireTime(reminder, now).orElse(null));
        if (reminder.getNextFireTime() == null) {
            // A cron with no future occurrence can never fire; say so rather than storing a dead row.
            throw new UserTaskReminderException(ErrorCode.INVALID_CRON_EXPRESSION, reminder.getCron());
        }

        Reminder saved = reminderRepository.save(reminder);
        log.info("Scheduled {} reminder {} for task {} at {}",
                saved.isRecurring() ? "recurring" : "one-time",
                saved.getId(), saved.getTask().getId(), saved.getNextFireTime());
        return mapper.map(saved, ReminderDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public ReminderDto get(UUID id) {
        return mapper.map(findOrThrow(id), ReminderDto.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReminderDto> list() {
        return reminderRepository.findAll().stream()
                .map(reminder -> mapper.map(reminder, ReminderDto.class))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReminderDto> listByTask(UUID taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new UserTaskReminderException(ErrorCode.TASK_NOT_FOUND, taskId);
        }
        return reminderRepository.findByTaskIdOrderByNextFireTimeAsc(taskId).stream()
                .map(reminder -> mapper.map(reminder, ReminderDto.class))
                .toList();
    }

    /**
     * Partial update. Changing the cron or the due date recomputes the next fire
     * time immediately, so the change takes effect on the very next poll.
     */
    @Override
    @Transactional
    public ReminderDto update(UUID id, ReminderDto request) {
        Reminder existing = findOrThrow(id);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean rescheduleNeeded = false;

        if (request.getMessage() != null) {
            existing.setMessage(request.getMessage());
        }
        if (request.getChannel() != null) {
            existing.setChannel(request.getChannel());
        }
        if (request.getTaskId() != null) {
            existing.setTask(resolveTask(request.getTaskId()));
        }
        if (request.getCron() != null) {
            String cron = normalise(request.getCron());
            if (cron != null) {
                calculator.validate(cron);
            }
            existing.setCron(cron);
            rescheduleNeeded = true;
        }
        if (request.getDueDate() != null) {
            existing.setDueDate(request.getDueDate());
            rescheduleNeeded = true;
        }

        if (rescheduleNeeded) {
            existing.setStatus(ReminderStatus.SCHEDULED);
            existing.setNextFireTime(calculator.initialFireTime(existing, now).orElse(null));
            if (existing.getNextFireTime() == null) {
                throw new UserTaskReminderException(ErrorCode.INVALID_REMINDER_SCHEDULE);
            }
        }

        return mapper.map(reminderRepository.save(existing), ReminderDto.class);
    }

    @Override
    @Transactional
    public ReminderDto cancel(UUID id) {
        Reminder existing = findOrThrow(id);
        existing.setStatus(ReminderStatus.CANCELLED);
        existing.setNextFireTime(null);
        log.info("Cancelled reminder {}", id);
        return mapper.map(reminderRepository.save(existing), ReminderDto.class);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        reminderRepository.delete(findOrThrow(id));
        log.info("Deleted reminder {}", id);
    }

    @Override
    @Transactional
    public int fireDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Reminder> due = reminderRepository.findDue(ReminderStatus.SCHEDULED, now);
        if (due.isEmpty()) {
            return 0;
        }
        due.forEach(reminder -> fire(reminder, now, false));
        reminderRepository.saveAll(due);
        return due.size();
    }

    @Override
    @Transactional
    public int recoverMissedReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Reminder> missed = reminderRepository.findDue(ReminderStatus.SCHEDULED, now);
        if (missed.isEmpty()) {
            log.info("Startup reminder recovery found nothing overdue");
            return 0;
        }
        missed.forEach(reminder -> fire(reminder, now, true));
        reminderRepository.saveAll(missed);
        log.info("Startup reminder recovery fired {} overdue reminder(s)", missed.size());
        return missed.size();
    }

    /**
     * Fires one reminder: raises the notification, stamps the fire, then
     * reschedules. Recurring reminders roll forward to their next future
     * occurrence; one-time reminders terminate as FIRED.
     */
    private void fire(Reminder reminder, LocalDateTime now, boolean recovery) {
        LocalDateTime scheduledFor = reminder.getNextFireTime();
        if (recovery && scheduledFor != null) {
            log.warn("Reminder {} is {} late; firing once and rescheduling",
                    reminder.getId(), Duration.between(scheduledFor, now));
        }

        notificationService.raise(
                reminder.getTask() == null ? null : reminder.getTask().getId(),
                ownerOf(reminder),
                messageFor(reminder),
                reminder.getChannel());

        reminder.setLastFiredAt(now);
        reminder.setFireCount(reminder.getFireCount() + 1);

        if (reminder.isRecurring()) {
            Optional<LocalDateTime> next = calculator.catchUp(reminder.getCron(), scheduledFor, now);
            if (next.isPresent()) {
                reminder.setNextFireTime(next.get());
            } else {
                // Schedule exhausted, e.g. a cron pinned to a date that has passed.
                reminder.setNextFireTime(null);
                reminder.setStatus(ReminderStatus.FIRED);
            }
        } else {
            reminder.setNextFireTime(null);
            reminder.setStatus(ReminderStatus.FIRED);
        }
    }

    private UUID ownerOf(Reminder reminder) {
        Task task = reminder.getTask();
        if (task == null || task.getCreatedBy() == null) {
            return null;
        }
        return task.getCreatedBy().getId();
    }

    private String messageFor(Reminder reminder) {
        if (reminder.getMessage() != null && !reminder.getMessage().isBlank()) {
            return reminder.getMessage();
        }
        String taskName = reminder.getTask() == null ? "task" : reminder.getTask().getName();
        return "Reminder: " + taskName;
    }

    private void assertScheduleShape(ReminderDto request) {
        boolean hasCron = request.getCron() != null && !request.getCron().isBlank();
        boolean hasDueDate = request.getDueDate() != null;
        if (hasCron == hasDueDate) {
            throw new UserTaskReminderException(ErrorCode.INVALID_REMINDER_SCHEDULE);
        }
    }

    private String normalise(String cron) {
        if (cron == null) {
            return null;
        }
        String trimmed = cron.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Task resolveTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new UserTaskReminderException(ErrorCode.TASK_NOT_FOUND, taskId));
    }

    private Reminder findOrThrow(UUID id) {
        return reminderRepository.findById(id)
                .orElseThrow(() -> new UserTaskReminderException(ErrorCode.REMINDER_NOT_FOUND, id));
    }
}
