package com.neko.notification;

import com.neko.TestFixtures;
import com.neko.entity.Notification;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDispatcherTest {

    private final NotificationProperties properties = new NotificationProperties();

    /** A sender that records what it was given, or fails on demand. */
    private static final class RecordingSender implements NotificationSender {

        private final Channel channel;
        private final boolean fail;
        private int calls;

        private RecordingSender(Channel channel, boolean fail) {
            this.channel = channel;
            this.fail = fail;
        }

        @Override
        public Channel channel() {
            return channel;
        }

        @Override
        public void send(Notification notification) {
            calls++;
            if (fail) {
                throw new NotificationDeliveryException("channel is down");
            }
        }
    }

    private NotificationDispatcher dispatcherWith(NotificationSender... senders) {
        return new NotificationDispatcher(List.of(senders), properties, TestFixtures.fixedClock());
    }

    @Test
    void routesToTheSenderRegisteredForTheChannel() {
        RecordingSender web = new RecordingSender(Channel.WEB, false);
        RecordingSender email = new RecordingSender(Channel.EMAIL, false);
        Notification notification = TestFixtures.notification(TestFixtures.user(), null);
        notification.setChannel(Channel.EMAIL);

        assertThat(dispatcherWith(web, email).dispatch(notification)).isTrue();

        assertThat(email.calls).isEqualTo(1);
        assertThat(web.calls).isZero();
        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(notification.getDeliveryAttempts()).isEqualTo(1);
        assertThat(notification.getLastAttemptAt()).isEqualTo(TestFixtures.NOW);
    }

    @Test
    void fallsBackToTheConfiguredDefaultChannelWhenNoneIsSet() {
        RecordingSender web = new RecordingSender(Channel.WEB, false);
        Notification notification = TestFixtures.notification(TestFixtures.user(), null);
        notification.setChannel(null);

        assertThat(dispatcherWith(web).dispatch(notification)).isTrue();

        assertThat(notification.getChannel()).isEqualTo(Channel.WEB);
        assertThat(web.calls).isEqualTo(1);
    }

    @Test
    void abandonsAChannelThatHasNoSender() {
        Notification notification = TestFixtures.notification(TestFixtures.user(), null);
        notification.setChannel(Channel.SMS);

        assertThat(dispatcherWith(new RecordingSender(Channel.WEB, false)).dispatch(notification)).isFalse();

        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.ABANDONED);
        assertThat(notification.getFailureReason()).contains("No sender registered");
    }

    @Test
    void marksAFailureRetryableWhileTheAttemptBudgetRemains() {
        properties.setMaxDeliveryAttempts(3);
        Notification notification = TestFixtures.notification(TestFixtures.user(), null);
        notification.setChannel(Channel.WEB);

        assertThat(dispatcherWith(new RecordingSender(Channel.WEB, true)).dispatch(notification)).isFalse();

        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(notification.getDeliveryAttempts()).isEqualTo(1);
        assertThat(notification.getFailureReason()).isEqualTo("channel is down");
    }

    @Test
    void abandonsOnceTheAttemptBudgetIsSpent() {
        properties.setMaxDeliveryAttempts(2);
        NotificationDispatcher dispatcher = dispatcherWith(new RecordingSender(Channel.WEB, true));
        Notification notification = TestFixtures.notification(TestFixtures.user(), null);
        notification.setChannel(Channel.WEB);

        dispatcher.dispatch(notification);
        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);

        dispatcher.dispatch(notification);
        assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.ABANDONED);
        assertThat(notification.getDeliveryAttempts()).isEqualTo(2);
    }

    @Test
    void reportsTheChannelsItCanServe() {
        NotificationDispatcher dispatcher = dispatcherWith(
                new RecordingSender(Channel.WEB, false), new RecordingSender(Channel.EMAIL, false));

        assertThat(dispatcher.supportedChannels()).containsExactlyInAnyOrder(Channel.WEB, Channel.EMAIL);
    }
}
