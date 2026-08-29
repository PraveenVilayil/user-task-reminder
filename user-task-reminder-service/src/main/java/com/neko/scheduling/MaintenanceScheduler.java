package com.neko.scheduling;

import com.neko.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic housekeeping: overdue task detection and notification delivery
 * retries. Both jobs are idempotent and safe to run on every tick.
 */
@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final OverdueTaskSweeper overdueTaskSweeper;
    private final NotificationService notificationService;

    public MaintenanceScheduler(OverdueTaskSweeper overdueTaskSweeper, NotificationService notificationService) {
        this.overdueTaskSweeper = overdueTaskSweeper;
        this.notificationService = notificationService;
    }

    @Scheduled(
            initialDelayString = "${app.scheduler.overdue-initial-delay-ms:20000}",
            fixedDelayString = "${app.scheduler.overdue-poll-interval-ms:300000}")
    public void sweepOverdueTasks() {
        try {
            overdueTaskSweeper.sweep();
        } catch (RuntimeException exception) {
            log.error("Overdue task sweep failed", exception);
        }
    }

    @Scheduled(
            initialDelayString = "${app.scheduler.delivery-retry-initial-delay-ms:30000}",
            fixedDelayString = "${app.scheduler.delivery-retry-interval-ms:120000}")
    public void retryFailedDeliveries() {
        try {
            notificationService.retryFailedDeliveries();
        } catch (RuntimeException exception) {
            log.error("Notification delivery retry failed", exception);
        }
    }
}
