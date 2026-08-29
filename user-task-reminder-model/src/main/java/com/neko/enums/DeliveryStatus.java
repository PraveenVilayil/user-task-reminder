package com.neko.enums;

/**
 * Delivery state of a {@link com.neko.entity.Notification} for its channel.
 */
public enum DeliveryStatus {
    /** Created but not yet handed to a sender. */
    PENDING,
    /** Accepted by the channel sender. */
    DELIVERED,
    /** Sender failed; eligible for retry until the attempt limit is reached. */
    FAILED,
    /** Retry budget exhausted, or no sender is registered for the channel. */
    ABANDONED
}
