package com.onlinebanking.services;

import com.onlinebanking.entities.AuditLog;
import com.onlinebanking.repositories.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Centralized service for recording security audit events.
 *
 * <p>Designed to be reusable by:
 * <ul>
 *   <li>Security exception handlers (401 / 403).</li>
 *   <li>Future business services (e.g. TransferService recording transfer attempts).</li>
 * </ul>
 *
 * <p>If the database write fails, the failure is logged to the application log
 * without propagating the exception — the security response to the client
 * must never be disrupted by an audit persistence failure.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record a security event in the audit log.
     *
     * @param eventType       classification (e.g. AUTHENTICATION_FAILURE, AUTHORIZATION_FAILURE)
     * @param userId          Keycloak {@code sub} claim, may be null
     * @param username        Keycloak {@code preferred_username}, may be null
     * @param httpMethod      HTTP method (GET, POST, …)
     * @param requestUri      requested URI path
     * @param statusCode      HTTP status code returned
     * @param remoteIpAddress client IP address
     * @param description     sanitized human-readable description
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logSecurityEvent(String eventType,
                                 String userId,
                                 String username,
                                 String httpMethod,
                                 String requestUri,
                                 int statusCode,
                                 String remoteIpAddress,
                                 String description) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType(eventType);
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setHttpMethod(httpMethod);
            auditLog.setRequestUri(requestUri);
            auditLog.setStatusCode(statusCode);
            auditLog.setRemoteIpAddress(remoteIpAddress);
            auditLog.setDescription(sanitize(description));
            auditLog.setTimestamp(LocalDateTime.now());

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Never crash the security handler — log and move on
            logger.error("Failed to persist security audit event [{}]: {}", eventType, e.getMessage(), e);
        }
    }

    /**
     * Record a financial event within the active transaction boundary.
     * If the transaction rolls back, this audit log will roll back with it.
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRED)
    public void logTransactionalEvent(String eventType,
                                      String userId,
                                      String username,
                                      String httpMethod,
                                      String requestUri,
                                      int statusCode,
                                      String remoteIpAddress,
                                      String description) {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventType(eventType);
        auditLog.setUserId(userId);
        auditLog.setUsername(username);
        auditLog.setHttpMethod(httpMethod);
        auditLog.setRequestUri(requestUri);
        auditLog.setStatusCode(statusCode);
        auditLog.setRemoteIpAddress(remoteIpAddress);
        auditLog.setDescription(sanitize(description));
        auditLog.setTimestamp(LocalDateTime.now());

        auditLogRepository.save(auditLog);
    }

    /**
     * Sanitize the description to avoid leaking internal details.
     * Truncates to 1000 characters and strips potential stack-trace fragments.
     */
    private String sanitize(String description) {
        if (description == null) {
            return null;
        }
        // Truncate to column max-length
        String sanitized = description.length() > 1000
                ? description.substring(0, 997) + "..."
                : description;
        // Strip anything that looks like a package-qualified exception
        return sanitized.replaceAll("(?:at )?[a-z]+(?:\\.[a-z]+)+\\.[A-Z]\\w*(?:\\.\\w+)*\\(.*\\)", "[redacted]");
    }
}

