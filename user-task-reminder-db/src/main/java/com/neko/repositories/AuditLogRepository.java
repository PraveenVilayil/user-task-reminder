package com.neko.repositories;

import com.neko.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTaskIdOrderByTimestampDesc(UUID taskId);

    Page<AuditLog> findByTaskIdOrderByTimestampDesc(UUID taskId, Pageable pageable);
}
