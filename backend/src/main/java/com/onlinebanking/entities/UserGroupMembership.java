package com.onlinebanking.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Join entity linking a {@link UserProfile} to an {@link IdentityGroup}.
 *
 * <p>Memberships are synchronized from the Keycloak JWT {@code groups} claim.
 * A unique constraint prevents duplicate memberships.
 */
@Entity
@Table(
    name = "user_group_memberships",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_group",
            columnNames = {"user_profile_id", "identity_group_id"}
        )
    },
    indexes = {
        @Index(name = "idx_ugm_user_profile", columnList = "user_profile_id"),
        @Index(name = "idx_ugm_identity_group", columnList = "identity_group_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class UserGroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    /** Eagerly fetched because group info is almost always needed with the membership. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "identity_group_id", nullable = false)
    private IdentityGroup identityGroup;

    /** When this membership was last confirmed by a JWT sync. */
    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @PrePersist
    protected void onPersist() {
        if (syncedAt == null) {
            syncedAt = LocalDateTime.now();
        }
    }
}

