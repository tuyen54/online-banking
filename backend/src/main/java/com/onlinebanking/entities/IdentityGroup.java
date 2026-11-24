package com.onlinebanking.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Local mirror of a Keycloak group.
 *
 * <p>Groups are synchronized from the JWT {@code groups} claim and stored
 * with their full path to avoid collisions between nested groups with identical names.
 *
 * <p>This table is an informational organizational mirror — not an independent
 * authorization authority. Keycloak remains the source of truth for group membership.
 * Realm roles (not groups) determine Spring Security authorities.
 */
@Entity
@Table(
    name = "identity_groups",
    indexes = {
        @Index(name = "idx_ig_group_path", columnList = "keycloak_group_path", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
public class IdentityGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Full Keycloak group path (e.g. {@code /Staff/Branch-HCM}). Used as sync key. */
    @Column(name = "keycloak_group_path", unique = true, nullable = false, length = 500)
    private String keycloakGroupPath;

    /** Short display name (last segment of the path). */
    @Column(name = "group_name", nullable = false, length = 255)
    private String groupName;

    /** Keycloak internal group ID, if available from token claims. */
    @Column(name = "keycloak_group_id", length = 255)
    private String keycloakGroupId;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @PrePersist
    @PreUpdate
    protected void onPersist() {
        lastSyncedAt = LocalDateTime.now();
    }
}

