package com.neko.repositories;

import com.neko.entity.Notification;
import com.neko.entity.User;
import com.neko.enums.Channel;
import com.neko.enums.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificationRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 2, 9, 0);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User recipient;

    @BeforeEach
    void setUp() {
        recipient = new User();
        recipient.setId(UUID.randomUUID());
        recipient.setUserName("recipient-" + UUID.randomUUID());
        recipient.setEmail(recipient.getUserName() + "@example.com");
        recipient.setRoles(new ArrayList<>());
        userRepository.saveAndFlush(recipient);
    }

    private Notification notification(DeliveryStatus status, int attempts, LocalDateTime createdDate) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUser(recipient);
        notification.setMessage("heads up");
        notification.setSeen(false);
        notification.setCreatedDate(createdDate);
        notification.setChannel(Channel.WEB);
        notification.setDeliveryStatus(status);
        notification.setDeliveryAttempts(attempts);
        return notification;
    }

    @Test
    void returnsAUserNotificationsNewestFirst() {
        Notification older = notificationRepository.saveAndFlush(
                notification(DeliveryStatus.DELIVERED, 1, NOW.minusDays(1)));
        Notification newer = notificationRepository.saveAndFlush(
                notification(DeliveryStatus.DELIVERED, 1, NOW));
        entityManager.clear();

        assertThat(notificationRepository.findByUserIdOrderByCreatedDateDesc(recipient.getId()))
                .extracting(Notification::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void buildsTheRetryBacklogFromPendingAndFailedBelowTheAttemptLimit() {
        Notification pending = notificationRepository.saveAndFlush(notification(DeliveryStatus.PENDING, 0, NOW));
        Notification failedOnce = notificationRepository.saveAndFlush(notification(DeliveryStatus.FAILED, 1, NOW));
        notificationRepository.saveAndFlush(notification(DeliveryStatus.FAILED, 3, NOW));
        notificationRepository.saveAndFlush(notification(DeliveryStatus.DELIVERED, 1, NOW));
        notificationRepository.saveAndFlush(notification(DeliveryStatus.ABANDONED, 1, NOW));
        entityManager.clear();

        List<Notification> backlog = notificationRepository.findByDeliveryStatusInAndDeliveryAttemptsLessThan(
                List.of(DeliveryStatus.PENDING, DeliveryStatus.FAILED), 3);

        assertThat(backlog).extracting(Notification::getId)
                .containsExactlyInAnyOrder(pending.getId(), failedOnce.getId());
    }

    @Test
    void deletingAUserRemovesTheirNotifications() {
        UUID notificationId = notificationRepository.saveAndFlush(
                notification(DeliveryStatus.DELIVERED, 1, NOW)).getId();
        entityManager.clear();

        userRepository.delete(userRepository.findById(recipient.getId()).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(notificationRepository.findById(notificationId)).isEmpty();
    }
}
