package com.onlinebanking.entities;

import com.onlinebanking.entities.enums.TransactionStatus;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing an executed internal money transfer.
 *
 * <p>Financial integrity rules:
 * <ul>
 *   <li>Amount must be strictly positive.</li>
 *   <li>Transaction reference is unique across the entire banking system.</li>
 *   <li>Idempotency key is scoped uniquely to the initiating customer profile.</li>
 *   <li>Financial attributes are immutable after completion.</li>
 * </ul>
 */
@Entity
@Table(
    name = "bank_transactions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tx_reference", columnNames = {"transaction_reference"}),
        @UniqueConstraint(name = "uk_tx_initiator_idempotency", columnNames = {"initiator_id", "idempotency_key"})
    },
    indexes = {
        @Index(name = "idx_tx_source_acc", columnList = "source_account_id"),
        @Index(name = "idx_tx_dest_acc", columnList = "destination_account_id"),
        @Index(name = "idx_tx_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Unique human-readable transaction reference (e.g. TRF-A1B2C3D4E5F6). */
    @Column(name = "transaction_reference", unique = true, nullable = false, length = 40)
    private String transactionReference;

    /** Customer who initiated the transfer. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiator_id", nullable = false)
    private UserProfile initiator;

    /** Source account debited in this transfer. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false)
    private BankAccount sourceAccount;

    /** Destination account credited in this transfer. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private BankAccount destinationAccount;

    /** Transfer amount with financial precision. Must be positive. */
    @DecimalMin(value = "0.01", message = "Transfer amount must be positive")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** 3-character ISO currency code (VND). */
    @Column(nullable = false, length = 3)
    private String currency = "VND";

    /** Optional customer-supplied transfer description. */
    @Column(length = 500)
    private String description;

    /** Transaction lifecycle status (COMPLETED, REJECTED, PENDING). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.COMPLETED;

    /** Client-provided idempotency key from HTTP header. */
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /** SHA-256 fingerprint of the normalized request parameters. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (completedAt == null && status == TransactionStatus.COMPLETED) completedAt = now;
    }
}

