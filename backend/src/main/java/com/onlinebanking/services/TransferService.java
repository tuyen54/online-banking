package com.onlinebanking.services;

import com.onlinebanking.dto.TransferRequest;
import com.onlinebanking.dto.TransferResponse;
import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.entities.BankTransaction;
import com.onlinebanking.entities.LedgerEntry;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.LedgerEntryType;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.entities.enums.TransactionStatus;
import com.onlinebanking.exceptions.IdempotencyConflictException;
import com.onlinebanking.repositories.BankAccountRepository;
import com.onlinebanking.repositories.BankTransactionRepository;
import com.onlinebanking.repositories.LedgerEntryRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

/**
 * Core engine for executing secure internal money transfers.
 *
 * <p>Key Guarantees:
 * <ul>
 *   <li>Atomic execution with {@code @Transactional(rollbackFor = Exception.class)}.</li>
 *   <li>Double-entry ledger invariant: exactly 1 DEBIT and 1 CREDIT entry of identical amount.</li>
 *   <li>Idempotency protection: SHA-256 payload hashing per {@code (initiator_id, idempotency_key)}.</li>
 *   <li>Deadlock-free pessimistic locking: locks accounts in deterministic UUID order.</li>
 *   <li>Full ABAC ownership check and account status verification.</li>
 * </ul>
 */
@Service
public class TransferService {

    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);

    private final BankAccountRepository bankAccountRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final AuditService auditService;
    private final EntityManager entityManager;

    public TransferService(BankAccountRepository bankAccountRepository,
                           BankTransactionRepository bankTransactionRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           UserProfileRepository userProfileRepository,
                           AuthorizationPolicyService authorizationPolicyService,
                           AuditService auditService,
                           EntityManager entityManager) {
        this.bankAccountRepository = bankAccountRepository;
        this.bankTransactionRepository = bankTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationPolicyService = authorizationPolicyService;
        this.auditService = auditService;
        this.entityManager = entityManager;
    }

    /**
     * Executes an internal transfer between two active accounts.
     *
     * @param jwt            verified caller JWT
     * @param idempotencyKey client-supplied idempotency key
     * @param request        validated transfer request parameters
     * @return transfer outcome response
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public TransferResponse executeTransfer(Jwt jwt, String idempotencyKey, TransferRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 100 characters");
        }

        String sub = extractSub(jwt);

        UserProfile initiator = userProfileRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> new AccessDeniedException("Access denied: User profile not found"));

        if (initiator.getProfileStatus() != ProfileStatus.ACTIVE) {
            throw new AccessDeniedException("Access denied: Initiator profile is not active");
        }

        String requestHash = computeRequestHash(request);

        // 1. Idempotency Check
        Optional<BankTransaction> existingTxOpt = bankTransactionRepository
                .findByInitiatorIdAndIdempotencyKey(initiator.getId(), idempotencyKey);

        if (existingTxOpt.isPresent()) {
            BankTransaction existingTx = existingTxOpt.get();
            if (existingTx.getRequestHash().equals(requestHash)) {
                logger.info("Idempotent replay for key [{}] and initiator [{}]", idempotencyKey, initiator.getId());
                return TransferResponse.from(existingTx);
            } else {
                auditService.logSecurityEvent(
                        "IDEMPOTENCY_CONFLICT",
                        sub,
                        initiator.getUsername(),
                        "POST",
                        "/api/transfers",
                        409,
                        null,
                        "Idempotency key reused with mismatched request payload: " + idempotencyKey
                );
                throw new IdempotencyConflictException("Idempotency-Key already used with a different request payload");
            }
        }

        // 2. Pre-lock Validations & ABAC
        authorizationPolicyService.assertCustomerOwnsAccount(request.getSourceAccountId(), sub);

        BankAccount destAccount = bankAccountRepository.findByAccountNumber(request.getDestinationAccountNumber().trim())
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        if (request.getSourceAccountId().equals(destAccount.getId())) {
            throw new IllegalArgumentException("Cannot transfer funds to the same account");
        }

        if (destAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Destination account is not active");
        }

        if (destAccount.getOwner().getProfileStatus() != ProfileStatus.ACTIVE) {
            throw new IllegalStateException("Destination account owner is not active");
        }

        if (!"VND".equalsIgnoreCase(destAccount.getCurrency())) {
            throw new IllegalArgumentException("Currency mismatch: transfers only supported in VND");
        }

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot have more than 2 decimal places");
        }

        // 3. Concurrency-Safe Pessimistic Locking (Deterministic order by UUID to prevent deadlocks)
        UUID srcId = request.getSourceAccountId();
        UUID dstId = destAccount.getId();
        BankAccount lockedSource;
        BankAccount lockedDest;

        if (srcId.compareTo(dstId) < 0) {
            lockedSource = entityManager.find(BankAccount.class, srcId);
            if (lockedSource == null) throw new IllegalArgumentException("Source account not found");
            entityManager.refresh(lockedSource, LockModeType.PESSIMISTIC_WRITE);

            lockedDest = entityManager.find(BankAccount.class, dstId);
            if (lockedDest == null) throw new IllegalArgumentException("Destination account not found");
            entityManager.refresh(lockedDest, LockModeType.PESSIMISTIC_WRITE);
        } else {
            lockedDest = entityManager.find(BankAccount.class, dstId);
            if (lockedDest == null) throw new IllegalArgumentException("Destination account not found");
            entityManager.refresh(lockedDest, LockModeType.PESSIMISTIC_WRITE);

            lockedSource = entityManager.find(BankAccount.class, srcId);
            if (lockedSource == null) throw new IllegalArgumentException("Source account not found");
            entityManager.refresh(lockedSource, LockModeType.PESSIMISTIC_WRITE);
        }

        // 4. Invariant checks under exclusive write lock
        if (lockedSource.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Source account is not active: " + lockedSource.getAccountStatus());
        }
        if (lockedDest.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Destination account is not active: " + lockedDest.getAccountStatus());
        }

        if (lockedSource.getBalance().compareTo(amount) < 0) {
            auditService.logSecurityEvent(
                    "TRANSFER_INSUFFICIENT_FUNDS",
                    sub,
                    initiator.getUsername(),
                    "POST",
                    "/api/transfers",
                    400,
                    null,
                    String.format("Transfer rejected: balance %s < requested %s", lockedSource.getBalance(), amount)
            );
            throw new IllegalArgumentException("Insufficient balance in source account");
        }

        // 5. Debit & Credit Balance Execution
        lockedSource.setBalance(lockedSource.getBalance().subtract(amount));
        lockedDest.setBalance(lockedDest.getBalance().add(amount));
        bankAccountRepository.save(lockedSource);
        bankAccountRepository.save(lockedDest);

        // 6. Record BankTransaction
        String ref = "TRF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        BankTransaction tx = new BankTransaction();
        tx.setTransactionReference(ref);
        tx.setInitiator(initiator);
        tx.setSourceAccount(lockedSource);
        tx.setDestinationAccount(lockedDest);
        tx.setAmount(amount);
        tx.setCurrency("VND");
        tx.setDescription(request.getDescription());
        tx.setStatus(TransactionStatus.COMPLETED);
        tx.setIdempotencyKey(idempotencyKey);
        tx.setRequestHash(requestHash);
        BankTransaction savedTx = bankTransactionRepository.save(tx);

        // 7. Double-Entry Ledger Bookkeeping
        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setBankTransaction(savedTx);
        debitEntry.setBankAccount(lockedSource);
        debitEntry.setEntryType(LedgerEntryType.DEBIT);
        debitEntry.setAmount(amount);
        debitEntry.setCurrency("VND");
        ledgerEntryRepository.save(debitEntry);

        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setBankTransaction(savedTx);
        creditEntry.setBankAccount(lockedDest);
        creditEntry.setEntryType(LedgerEntryType.CREDIT);
        creditEntry.setAmount(amount);
        creditEntry.setCurrency("VND");
        ledgerEntryRepository.save(creditEntry);

        // Accounting balance assertion
        if (debitEntry.getAmount().compareTo(creditEntry.getAmount()) != 0) {
            throw new IllegalStateException("Ledger balance invariant violated: DEBIT and CREDIT amounts differ");
        }

        // 8. Transactional Audit Log
        auditService.logTransactionalEvent(
                "TRANSFER_COMPLETED",
                sub,
                initiator.getUsername(),
                "POST",
                "/api/transfers",
                200,
                null,
                String.format("Internal transfer executed: ref=%s, amount=%s VND, src=%s, dst=%s",
                        savedTx.getTransactionReference(),
                        amount,
                        AccountNumberGenerator.mask(lockedSource.getAccountNumber()),
                        AccountNumberGenerator.mask(lockedDest.getAccountNumber()))
        );

        logger.info("Transfer completed successfully [ref={}, amount={}]", ref, amount);
        return TransferResponse.from(savedTx);
    }

    /**
     * Computes a deterministic SHA-256 hash of the normalized request parameters.
     */
    public static String computeRequestHash(TransferRequest req) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String normAmount = req.getAmount() != null ? req.getAmount().stripTrailingZeros().toPlainString() : "";
            String raw = (req.getSourceAccountId() != null ? req.getSourceAccountId().toString() : "") + "|"
                    + (req.getDestinationAccountNumber() != null ? req.getDestinationAccountNumber().trim() : "") + "|"
                    + normAmount + "|"
                    + (req.getDescription() != null ? req.getDescription().trim() : "");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String extractSub(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Access denied: Missing authenticated subject");
        }
        return jwt.getSubject();
    }
}

