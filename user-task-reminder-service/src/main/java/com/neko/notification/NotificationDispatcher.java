package com.neko.notification;

import com.neko.entity.Notification;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Routes a notification to the {@link NotificationSender} registered for its
 * channel and records the outcome on the entity.
 *
 * <p>The dispatcher never throws: a delivery failure is data, not control flow.
 * It stamps {@code deliveryStatus}, increments {@code deliveryAttempts} and
 * moves the notification to {@code ABANDONED} once the attempt budget is spent,
 * so the retry sweep has a terminal state to stop at.</p>
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final Map<Channel, NotificationSender> senders = new EnumMap<>(Channel.class);
    private final NotificationProperties properties;
    private final Clock clock;

    public NotificationDispatcher(List<NotificationSender> senders, NotificationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        senders.forEach(sender -> this.senders.put(sender.channel(), sender));
        log.info("Notification dispatcher active for channels {}", this.senders.keySet());
    }

    /**
     * Attempts delivery and mutates the notification with the result.
     *
     * @return true when the notification is now DELIVERED
     */
    public boolean dispatch(Notification notification) {
        Channel channel = notification.getChannel() == null
                ? properties.getDefaultChannel()
                : notification.getChannel();
        notification.setChannel(channel);
        notification.setDeliveryAttempts(notification.getDeliveryAttempts() + 1);
        notification.setLastAttemptAt(LocalDateTime.now(clock));

        NotificationSender sender = senders.get(channel);
        if (sender == null) {
            notification.setDeliveryStatus(DeliveryStatus.ABANDONED);
            notification.setFailureReason("No sender registered for channel " + channel);
            log.warn("Notification {} abandoned: no sender for channel {}", notification.getId(), channel);
            return false;
        }

        try {
            sender.send(notification);
            notification.setDeliveryStatus(DeliveryStatus.DELIVERED);
            notification.setFailureReason(null);
            return true;
        } catch (RuntimeException exception) {
            boolean budgetSpent = notification.getDeliveryAttempts() >= properties.getMaxDeliveryAttempts();
            notification.setDeliveryStatus(budgetSpent ? DeliveryStatus.ABANDONED : DeliveryStatus.FAILED);
            notification.setFailureReason(truncate(exception.getMessage()));
            log.warn("Notification {} delivery attempt {} over {} failed: {}",
                    notification.getId(), notification.getDeliveryAttempts(), channel, exception.getMessage());
            return false;
        }
    }

    /** Channels with a registered sender, for diagnostics and tests. */
    public java.util.Set<Channel> supportedChannels() {
        return java.util.Collections.unmodifiableSet(senders.keySet());
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown failure";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
