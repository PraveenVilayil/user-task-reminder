package com.neko.scheduling;

import com.neko.entity.Reminder;
import com.neko.exceptions.ErrorCode.ErrorCode;
import com.neko.exceptions.UserTaskReminderException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * All fire-time arithmetic for reminders, kept free of persistence and of the
 * system clock so it can be unit tested exhaustively.
 *
 * <p>Two shapes are supported:</p>
 * <ul>
 *   <li><b>one-time</b> &mdash; no cron; fires once at its due date.</li>
 *   <li><b>recurring</b> &mdash; a Spring six-field cron expression; the next
 *       fire time is always strictly after the reference instant, so a reminder
 *       can never fire twice for the same occurrence.</li>
 * </ul>
 */
@Component
public class ReminderScheduleCalculator {

    /**
     * Upper bound on catch-up iterations. A one-second cron left unattended for
     * a year would otherwise spin; the bound turns that into a single fire.
     */
    private static final int MAX_CATCH_UP_STEPS = 10_000;

    /**
     * The first fire time for a freshly created reminder.
     *
     * @param reminder the reminder, already carrying its cron or due date
     * @param from     the reference instant, normally now
     * @return the first fire time, or empty when a cron expression has no future occurrence
     */
    public Optional<LocalDateTime> initialFireTime(Reminder reminder, LocalDateTime from) {
        if (reminder.isRecurring()) {
            return nextFireTime(reminder.getCron(), from);
        }
        return Optional.ofNullable(reminder.getDueDate());
    }

    /**
     * The first occurrence of {@code cron} strictly after {@code after}.
     *
     * @throws UserTaskReminderException with {@link ErrorCode#INVALID_CRON_EXPRESSION}
     *                                   when the expression cannot be parsed
     */
    public Optional<LocalDateTime> nextFireTime(String cron, LocalDateTime after) {
        return Optional.ofNullable(parse(cron).next(after));
    }

    /**
     * Rolls a recurring schedule forward past {@code now}.
     *
     * <p>Used when the application was down over one or more occurrences: the
     * reminder fires once for the missed window and is then rescheduled to its
     * next future occurrence, rather than replaying every occurrence it slept
     * through.</p>
     *
     * @param cron the cron expression
     * @param from the last known fire time
     * @param now  the current instant
     * @return the next occurrence strictly after {@code now}, or empty when the schedule is exhausted
     */
    public Optional<LocalDateTime> catchUp(String cron, LocalDateTime from, LocalDateTime now) {
        CronExpression expression = parse(cron);
        LocalDateTime cursor = from == null || from.isAfter(now) ? now : from;
        for (int step = 0; step < MAX_CATCH_UP_STEPS; step++) {
            LocalDateTime next = expression.next(cursor);
            if (next == null) {
                return Optional.empty();
            }
            if (next.isAfter(now)) {
                return Optional.of(next);
            }
            cursor = next;
        }
        // Bound hit: fall back to the first occurrence after now.
        return Optional.ofNullable(expression.next(now));
    }

    /** True when the reminder is past its fire time and still eligible to fire. */
    public boolean isDue(Reminder reminder, LocalDateTime now) {
        return reminder.getNextFireTime() != null && !reminder.getNextFireTime().isAfter(now);
    }

    /**
     * Validates a cron expression up front so a bad one is a 400 at creation
     * time rather than a scheduler failure hours later.
     */
    public void validate(String cron) {
        parse(cron);
    }

    private CronExpression parse(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new UserTaskReminderException(ErrorCode.INVALID_CRON_EXPRESSION, String.valueOf(cron));
        }
        try {
            return CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException exception) {
            throw new UserTaskReminderException(ErrorCode.INVALID_CRON_EXPRESSION, exception, cron);
        }
    }
}
