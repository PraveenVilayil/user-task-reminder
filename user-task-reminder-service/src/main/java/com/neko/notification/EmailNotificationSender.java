package com.neko.notification;

import com.neko.entity.Notification;
import com.neko.enums.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP channel.
 *
 * <p>Registered only when {@code app.notifications.email.enabled=true}, so the
 * default profile, the test profile and CI never require a mail server. With
 * Docker Compose, MailHog on port 1025 is a convenient target.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.notifications.email", name = "enabled", havingValue = "true")
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public EmailNotificationSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(Notification notification) {
        if (notification.getUser() == null || notification.getUser().getEmail() == null) {
            throw new NotificationDeliveryException(
                    "Notification " + notification.getId() + " has no recipient email address");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getEmail().getFrom());
        message.setTo(notification.getUser().getEmail());
        message.setSubject(properties.getEmail().getSubjectPrefix() + subjectFor(notification));
        message.setText(notification.getMessage() == null ? "" : notification.getMessage());

        try {
            mailSender.send(message);
            log.info("Emailed notification {} to {}", notification.getId(), notification.getUser().getEmail());
        } catch (MailException exception) {
            throw new NotificationDeliveryException(
                    "SMTP delivery failed for notification " + notification.getId(), exception);
        }
    }

    private String subjectFor(Notification notification) {
        if (notification.getTask() != null && notification.getTask().getName() != null) {
            return notification.getTask().getName();
        }
        return "Notification";
    }
}
