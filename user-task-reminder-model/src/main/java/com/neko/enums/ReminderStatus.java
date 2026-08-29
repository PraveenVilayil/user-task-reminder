package com.neko.enums;

/**
 * Lifecycle of a {@link com.neko.entity.Reminder}.
 */
public enum ReminderStatus {
    /** Waiting for its next fire time. */
    SCHEDULED,
    /** A one-time reminder that has already fired. */
    FIRED,
    /** Cancelled by a user; never fires again. */
    CANCELLED
}
