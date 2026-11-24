package com.onlinebanking.entities;

import com.onlinebanking.entities.enums.ProfileStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Local banking profile mapped to a Keycloak identity via the immutable {@code sub} claim.
 *
 * <p>Keycloak owns authentication and identity attributes. This entity stores
 * banking-specific information (branch, profile status) and mirrors select
 * identity attributes for display and query purposes.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>{@code keycloakUserId} is immutable after creation — it cannot be changed.</li>
 *   <li>No password fields — Keycloak owns authentication.</li>
 *   <li>Branch assignment is never modified during identity synchronization.</li>
 *   <li>Profile status is never modified during identity synchronization.</li>
 * </ul>
 */
@Entity
@Table(
    name = "user_profiles",
    indexes = {
        @Index(name = "idx_up_keycloak_user_id", columnList = "keycloak_user_id", unique = true),
        @Index(name = "idx_up_username", columnList = "username"),
        @Index(name = "idx_up_email", columnList = "email"),
        @Index(name = "idx_up_branch_id", columnList = "branch_id"),
        @Index(name = "idx_up_profile_status", columnList = "profile_status")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Immutable Keycloak {@code sub} claim — the primary external identity key. */
    @Column(name = "keycloak_user_id", unique = true, nullable = false, updatable = false, length = 255)
    private String keycloakUserId;

    /** Mirrored from Keycloak {@code preferred_username}. */
    @Column(length = 255)
    private String username;

    /** Mirrored from Keycloak {@code email}. */
    @Column(length = 255)
    private String email;

    /** Mirrored from Keycloak {@code given_name}. */
    @Column(name = "first_name", length = 255)
    private String firstName;

    /** Mirrored from Keycloak {@code family_name}. */
    @Column(name = "last_name", length = 255)
    private String lastName;

    /** Mirrored from Keycloak {@code name}. */
    @Column(name = "display_name", length = 500)
    private String displayName;

    /** Mirrored from Keycloak {@code email_verified}. */
    @Column(name = "email_verified")
    private Boolean emailVerified;

    /** Banking profile status — controlled by the backend, never by identity sync. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "profile_status", nullable = false, length = 20)
    private ProfileStatus profileStatus = ProfileStatus.ACTIVE;

    /** The user's assigned bank branch (optional for customers, required for staff). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Last time identity attributes were synchronized from a Keycloak JWT. */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    /** Optimistic locking version. */
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

