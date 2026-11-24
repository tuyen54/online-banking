package com.onlinebanking.repositories;

import com.onlinebanking.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data-access layer for {@link AuditLog} entities.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Find audit events by type (e.g. AUTHENTICATION_FAILURE). */
    List<AuditLog> findByEventType(String eventType);

    /** Find all audit events for a specific Keycloak user (by {@code sub} claim). */
    List<AuditLog> findByUserId(String userId);

    /** Find audit events within a time range, ordered newest-first. */
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime from, LocalDateTime to);

    /** Find audit events by event type within a time range. */
    List<AuditLog> findByEventTypeAndTimestampBetweenOrderByTimestampDesc(
            String eventType, LocalDateTime from, LocalDateTime to);
}

