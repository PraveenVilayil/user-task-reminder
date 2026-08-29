package com.neko.notification;

import com.neko.entity.Notification;
import com.neko.enums.Channel;

/**
 * Strategy for pushing a notification out over one delivery channel.
 *
 * <p>Implementations are discovered by Spring and indexed by
 * {@link #channel()} in {@link NotificationDispatcher}. A channel with no
 * registered sender is not an error at startup; notifications for it are simply
 * marked {@code ABANDONED} when dispatched.</p>
 */
public interface NotificationSender {

    /** The channel this sender serves. */
    Channel channel();

    /**
     * Delivers the notification.
     *
     * @throws NotificationDeliveryException when the channel rejected the message;
     *                                       the dispatcher will retry within the attempt budget
     */
    void send(Notification notification);
}
