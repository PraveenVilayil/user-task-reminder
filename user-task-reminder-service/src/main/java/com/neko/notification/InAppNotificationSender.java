package com.neko.notification;

import com.neko.entity.Notification;
import com.neko.enums.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default in-app channel. The notification row itself is the deliverable, so
 * "sending" is just acknowledging it; the log line gives an operator something
 * to trace. Always available, needs no external service.
 */
@Component
public class InAppNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationSender.class);

    @Override
    public Channel channel() {
        return Channel.WEB;
    }

    @Override
    public void send(Notification notification) {
        log.info("In-app notification {} for user {}: {}",
                notification.getId(),
                notification.getUser() == null ? "unassigned" : notification.getUser().getId(),
                notification.getMessage());
    }
}
