package com.onlinebanking.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persistent audit trail for security-relevant events.
 *
 * <p>Records authentication failures (401), authorization failures (403),
 * and — in future phases — sensitive business operations such as money transfers.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>Never stores raw JWT tokens, passwords, or MFA secrets.</li>
 *   <li>Description is sanitized — no internal exception details.</li>
 * </ul>
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Classification of the event (e.g. AUTHENTICATION_FAILURE, AUTHORIZATION_FAILURE). */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** Keycloak {@code sub} claim — null when the user is not authenticated. */
    @Column(name = "user_id", length = 255)
    private String userId;

    /** Keycloak {@code preferred_username} — null when the user is not authenticated. */
    @Column(name = "username", length = 255)
    private String username;

    /** HTTP method of the request (GET, POST, …). */
    @Column(name = "http_method", nullable = false, length = 20)
    private String httpMethod;

    /** Request URI (path only, no query string). */
    @Column(name = "request_uri", nullable = false, length = 500)
    private String requestUri;

    /** HTTP status code returned to the client. */
    @Column(name = "status_code", nullable = false)
    private Integer statusCode;

    /** Client IP address (trusted only when behind a correctly configured proxy). */
    @Column(name = "remote_ip_address", length = 45)
    private String remoteIpAddress;

    /** Human-readable, sanitized description of the event. */
    @Column(name = "description", length = 1000)
    private String description;

    /** When the event occurred. */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}

