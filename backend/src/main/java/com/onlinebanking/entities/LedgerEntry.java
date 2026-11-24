package com.onlinebanking.entities;

import com.onlinebanking.entities.enums.LedgerEntryType;
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
 * Immutable ledger entry for double-entry bookkeeping.
 *
 * <p>Mandatory accounting invariants:
 * <ul>
 *   <li>Every transfer creates exactly two entries: one DEBIT and one CREDIT.</li>
 *   <li>{@code SUM(DEBIT) == SUM(CREDIT)} for every transaction.</li>
 *   <li>Unique constraint prevents duplicate DEBIT or CREDIT entries per transaction.</li>
 *   <li>Append-only: entries are never updated or deleted.</li>
 * </ul>
 */
@Entity
@Table(
    name = "ledger_entries",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ledger_tx_entry", columnNames = {"transaction_id", "entry_type"})
    },
    indexes = {
        @Index(name = "idx_ledger_acc", columnList = "bank_account_id"),
        @Index(name = "idx_ledger_tx", columnList = "transaction_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Transaction associated with this ledger entry. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private BankTransaction bankTransaction;

    /** Bank account affected by this entry. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    /** Type of entry: DEBIT (reduction) or CREDIT (increase). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    /** Positive entry amount matching transaction currency. */
    @DecimalMin(value = "0.01", message = "Ledger amount must be positive")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** 3-character ISO currency code. */
    @Column(nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}

