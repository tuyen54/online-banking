package com.onlinebanking.repositories;

import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository enforcing owner-scoped queries and administrative lookup for BankAccount entities.
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    /** Find all accounts belonging to the owner identified by Keycloak sub. */
    List<BankAccount> findByOwnerKeycloakUserId(String keycloakUserId);

    /** Find an account by its ID and owner's Keycloak sub. */
    Optional<BankAccount> findByIdAndOwnerKeycloakUserId(UUID id, String keycloakUserId);

    /** Check if an account exists for a specific owner's Keycloak sub. */
    boolean existsByIdAndOwnerKeycloakUserId(UUID id, String keycloakUserId);

    /** Check if an account number already exists (for collision prevention). */
    boolean existsByAccountNumber(String accountNumber);

    /** Find an account by account number. */
    Optional<BankAccount> findByAccountNumber(String accountNumber);

    /** Pessimistic write lock by account ID for concurrency-safe balance updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BankAccount b WHERE b.id = :id")
    Optional<BankAccount> findByIdForUpdate(@Param("id") UUID id);

    /** Pessimistic write lock by account number for concurrency-safe balance updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BankAccount b WHERE b.accountNumber = :accountNumber")
    Optional<BankAccount> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    /** Find an account by ID and internal UserProfile ID. */
    Optional<BankAccount> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Find all accounts belonging to an internal UserProfile ID. */
    List<BankAccount> findByOwnerId(UUID ownerId);

    /** Administrative lookup with pagination by status. */
    Page<BankAccount> findByAccountStatus(AccountStatus status, Pageable pageable);

    /** Administrative lookup with pagination by type. */
    Page<BankAccount> findByAccountType(AccountType type, Pageable pageable);

    /** Administrative lookup with pagination by status and type. */
    Page<BankAccount> findByAccountStatusAndAccountType(AccountStatus status, AccountType type, Pageable pageable);
}
