package com.neko.repositories;

import com.neko.entity.Task;
import com.neko.enums.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    /**
     * Fetches the owner in the same query. Without this, listing N tasks issues
     * N extra selects for the eager many-to-one association.
     */
    @Override
    @EntityGraph(attributePaths = "createdBy")
    List<Task> findAll();

    @Override
    @EntityGraph(attributePaths = "createdBy")
    Page<Task> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "createdBy")
    Optional<Task> findById(UUID id);

    /**
     * Tasks whose due date has passed, that are not finished, and for which an
     * overdue notification has not been raised yet.
     */
    @Query("""
            select t from Task t
            left join fetch t.createdBy
            where t.dueDate is not null
              and t.dueDate < :now
              and t.status <> :completed
              and t.overdueNotifiedAt is null
            """)
    List<Task> findOverdue(@Param("now") LocalDateTime now, @Param("completed") Status completed);

    List<Task> findByCreatedById(UUID createdById);
}
