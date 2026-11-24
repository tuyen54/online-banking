package com.onlinebanking.repositories;

import com.onlinebanking.entities.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BankTransaction entity supporting idempotent execution and role-scoped history.
 */
@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    /** Lookup transaction by unique human-readable reference. */
    Optional<BankTransaction> findByTransactionReference(String transactionReference);

    /** Lookup previous transfer by initiator profile and client idempotency key. */
    Optional<BankTransaction> findByInitiatorIdAndIdempotencyKey(UUID initiatorId, String idempotencyKey);

    /**
     * History query for customer: all transactions where customer's Keycloak sub
     * is either source account owner or destination account owner.
     */
    @Query("SELECT t FROM BankTransaction t WHERE " +
           "t.sourceAccount.owner.keycloakUserId = :sub OR " +
           "t.destinationAccount.owner.keycloakUserId = :sub " +
           "ORDER BY t.createdAt DESC")
    Page<BankTransaction> findByCustomerKeycloakUserId(@Param("sub") String sub, Pageable pageable);

    /**
     * History query for staff: all transactions where either source account's owner branch
     * or destination account's owner branch matches the staff member's assigned branch.
     */
    @Query("SELECT t FROM BankTransaction t WHERE " +
           "t.sourceAccount.owner.branch.id = :branchId OR " +
           "t.destinationAccount.owner.branch.id = :branchId " +
           "ORDER BY t.createdAt DESC")
    Page<BankTransaction> findByBranchId(@Param("branchId") UUID branchId, Pageable pageable);

    /**
     * History query for auditor: chronological view of all completed transactions.
     */
    Page<BankTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

