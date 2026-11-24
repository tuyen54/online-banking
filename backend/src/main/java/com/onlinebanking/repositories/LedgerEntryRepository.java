package com.onlinebanking.repositories;

import com.onlinebanking.entities.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for immutable double-entry LedgerEntry records.
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /** Find all ledger entries for a transaction (should be exactly 2: 1 DEBIT, 1 CREDIT). */
    List<LedgerEntry> findByBankTransactionId(UUID transactionId);

    /** Find all ledger entries affecting a specific bank account. */
    Page<LedgerEntry> findByBankAccountIdOrderByCreatedAtDesc(UUID bankAccountId, Pageable pageable);
}

