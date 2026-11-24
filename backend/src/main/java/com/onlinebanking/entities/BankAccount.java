package com.onlinebanking.entities;

import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.AccountType;
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
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bank Account entity representing a customer's financial account.
 *
 * <p>Security & Architectural invariants:
 * <ul>
 *   <li>Every account has exactly one owner referencing a valid {@link UserProfile}.</li>
 *   <li>Account numbers are unique and must never be exposed unmasked in logs or APIs.</li>
 *   <li>Account ownership is immutable via customer-facing APIs.</li>
 *   <li>Balance uses high-precision {@link BigDecimal} and MySQL DECIMAL(19,2).</li>
 *   <li>Optimistic concurrency control is managed via {@link Version}.</li>
 * </ul>
 */
@Entity
@Table(
    name = "bank_accounts",
    indexes = {
        @Index(name = "idx_ba_account_number", columnList = "account_number", unique = true),
        @Index(name = "idx_ba_owner_id", columnList = "owner_id"),
        @Index(name = "idx_ba_status", columnList = "account_status")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique account number generated cryptographically by backend. */
    @Column(name = "account_number", unique = true, nullable = false, length = 30)
    private String accountNumber;

    /** Owner of the account. Must reference a real UserProfile. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserProfile owner;

    /** Account classification (CHECKING, SAVINGS). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType = AccountType.CHECKING;

    /** Account operational lifecycle state (ACTIVE, LOCKED, FROZEN, CLOSED). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    /** Available account balance with monetary precision. Initialized to zero. */
    @DecimalMin(value = "0.00", message = "Balance cannot be negative")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    /** 3-character ISO currency code (e.g. VND, USD). */
    @Column(nullable = false, length = 3)
    private String currency = "VND";

    /** When the account was opened. */
    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Optimistic locking version field to prevent stale concurrent writes. */
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (openedAt == null) openedAt = now;
        if (balance == null) balance = BigDecimal.ZERO;
        if (currency == null) currency = "VND";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
