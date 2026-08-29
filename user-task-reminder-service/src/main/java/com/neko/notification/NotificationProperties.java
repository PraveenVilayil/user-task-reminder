package com.neko.notification;

import com.neko.enums.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for notification delivery, bound from {@code app.notifications.*}.
 */
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

    /** Channel used when a notification does not name one. */
    private Channel defaultChannel = Channel.WEB;

    /** How many delivery attempts a notification gets before it is abandoned. */
    private int maxDeliveryAttempts = 3;

    /** Email channel settings. */
    private final Email email = new Email();

    public Channel getDefaultChannel() {
        return defaultChannel;
    }

    public void setDefaultChannel(Channel defaultChannel) {
        this.defaultChannel = defaultChannel;
    }

    public int getMaxDeliveryAttempts() {
        return maxDeliveryAttempts;
    }

    public void setMaxDeliveryAttempts(int maxDeliveryAttempts) {
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    public Email getEmail() {
        return email;
    }

    public static class Email {

        /**
         * Off by default so the application, its tests and CI never need an SMTP
         * server. Turn on with {@code app.notifications.email.enabled=true}.
         */
        private boolean enabled = false;

        private String from = "no-reply@user-task-reminder.local";

        private String subjectPrefix = "[Task Reminder] ";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getSubjectPrefix() {
            return subjectPrefix;
        }

        public void setSubjectPrefix(String subjectPrefix) {
            this.subjectPrefix = subjectPrefix;
        }
    }
}
