package com.onlinebanking.controllers;

import com.onlinebanking.entities.AuditLog;
import com.onlinebanking.repositories.AuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit log endpoints — accessible to ADMIN and AUDITOR roles.
 *
 * <p>Provides read-only access to the security audit trail.
 * Neither ADMIN nor AUDITOR can modify audit records through this endpoint.
 */
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Retrieve audit logs, optionally filtered by time range and event type.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to) {

        List<AuditLog> logs;

        if (from != null && to != null && eventType != null) {
            logs = auditLogRepository.findByEventTypeAndTimestampBetweenOrderByTimestampDesc(
                    eventType, from, to);
        } else if (from != null && to != null) {
            logs = auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(from, to);
        } else if (eventType != null) {
            logs = auditLogRepository.findByEventType(eventType);
        } else {
            logs = auditLogRepository.findAll();
        }

        return ResponseEntity.ok(logs);
    }
}

