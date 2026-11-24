package com.onlinebanking.entities;

import com.onlinebanking.entities.enums.BranchStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A physical bank branch — an organizational unit within the simulated bank.
 *
 * <p>Branch assignments are stored locally in MySQL and control STAFF data scoping.
 * A STAFF member must have an assigned branch before accessing branch-restricted
 * customer information (enforced by ABAC in Task 03).
 */
@Entity
@Table(
    name = "branches",
    indexes = {
        @Index(name = "idx_branch_code", columnList = "branch_code", unique = true),
        @Index(name = "idx_branch_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique human-readable branch code (e.g. BR-HCM, BR-HN). */
    @Column(name = "branch_code", unique = true, nullable = false, length = 20)
    private String branchCode;

    /** Display name of the branch. */
    @Column(name = "branch_name", nullable = false, length = 255)
    private String branchName;

    /** Physical address (optional). */
    @Column(length = 500)
    private String address;

    /** Whether this branch is currently active. Inactive branches cannot be assigned. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private BranchStatus status = BranchStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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

