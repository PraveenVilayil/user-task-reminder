package com.neko.service;

import com.neko.dto.ReminderDto;

import java.util.List;
import java.util.UUID;

public interface ReminderService {

    ReminderDto create(ReminderDto reminder);

    ReminderDto get(UUID id);

    List<ReminderDto> list();

    List<ReminderDto> listByTask(UUID taskId);

    ReminderDto update(UUID id, ReminderDto reminder);

    /** Stops a reminder from firing again without deleting its history. */
    ReminderDto cancel(UUID id);

    void delete(UUID id);

    /**
     * Fires every reminder whose next fire time has arrived, creating a
     * notification for each and rescheduling recurring ones.
     *
     * @return the number of reminders fired
     */
    int fireDueReminders();

    /**
     * Fires reminders whose fire time elapsed while the application was down,
     * then rolls recurring ones forward to their next future occurrence so a
     * long outage does not produce a burst of catch-up notifications.
     *
     * @return the number of missed reminders recovered
     */
    int recoverMissedReminders();
}
