package com.neko.repositories;

import com.neko.entity.Reminder;
import com.neko.enums.ReminderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    @Override
    @EntityGraph(attributePaths = {"task", "task.createdBy"})
    Optional<Reminder> findById(UUID id);

    @EntityGraph(attributePaths = {"task", "task.createdBy"})
    List<Reminder> findByTaskIdOrderByNextFireTimeAsc(UUID taskId);

    /**
     * Reminders that are due at or before the given instant. Also picks up
     * reminders whose fire time was missed while the application was down.
     */
    @Query("""
            select r from Reminder r
            left join fetch r.task t
            left join fetch t.createdBy
            where r.status = :status
              and r.nextFireTime is not null
              and r.nextFireTime <= :now
            order by r.nextFireTime asc
            """)
    List<Reminder> findDue(@Param("status") ReminderStatus status, @Param("now") LocalDateTime now);
}
