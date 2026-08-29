package com.neko.scheduling;

import com.neko.service.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives reminder firing.
 *
 * <p>A poll loop rather than one timer per reminder: reminders live in the
 * database, survive restarts and can be created from any instance, so a single
 * indexed query on {@code next_fire_time} is both simpler and safer than holding
 * scheduled futures in memory.</p>
 *
 * <p>Disable entirely with {@code app.scheduler.enabled=false} (the test profile
 * does exactly that, and calls the service directly instead).</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(
            initialDelayString = "${app.scheduler.reminder-initial-delay-ms:10000}",
            fixedDelayString = "${app.scheduler.reminder-poll-interval-ms:30000}")
    public void pollDueReminders() {
        try {
            int fired = reminderService.fireDueReminders();
            if (fired > 0) {
                log.info("Reminder poll fired {} reminder(s)", fired);
            }
        } catch (RuntimeException exception) {
            // Never let a failure kill the scheduled task; the next poll retries.
            log.error("Reminder poll failed", exception);
        }
    }

    /**
     * Catches up on reminders whose fire time passed while the application was
     * not running.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            reminderService.recoverMissedReminders();
        } catch (RuntimeException exception) {
            log.error("Startup reminder recovery failed", exception);
        }
    }
}
