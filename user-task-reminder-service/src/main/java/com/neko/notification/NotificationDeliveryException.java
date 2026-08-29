package com.neko.notification;

/**
 * Raised by a {@link NotificationSender} when a channel refuses a message.
 * Recoverable by design: the dispatcher retries until the attempt budget runs out.
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message) {
        super(message);
    }

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
