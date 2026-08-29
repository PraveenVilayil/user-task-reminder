package com.neko.repositories;

import com.neko.entity.Notification;
import com.neko.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Override
    @EntityGraph(attributePaths = {"user", "task"})
    List<Notification> findAll();

    @Override
    @EntityGraph(attributePaths = {"user", "task"})
    Page<Notification> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"user", "task"})
    Optional<Notification> findById(UUID id);

    @EntityGraph(attributePaths = {"user", "task"})
    List<Notification> findByUserIdOrderByCreatedDateDesc(UUID userId);

    /** Backlog for the delivery retry sweep. */
    @EntityGraph(attributePaths = {"user", "task"})
    List<Notification> findByDeliveryStatusInAndDeliveryAttemptsLessThan(
            Collection<DeliveryStatus> statuses, int maxAttempts);
}
