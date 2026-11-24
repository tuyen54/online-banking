package com.onlinebanking.services;

import com.onlinebanking.dto.TransactionSummaryResponse;
import com.onlinebanking.entities.BankTransaction;
import com.onlinebanking.entities.Branch;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.BranchStatus;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.repositories.BankTransactionRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service providing role-scoped, read-only transaction history.
 *
 * <p>Access Policies:
 * <ul>
 *   <li>CUSTOMER: Can only view transactions where they are sender or recipient.</li>
 *   <li>STAFF: Can view transactions where at least one party belongs to their active branch.</li>
 *   <li>AUDITOR: Full read-only chronological access to all completed transactions.</li>
 *   <li>ADMIN: Denied (no access to financial transactions or customer balances).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TransactionHistoryService {

    private final BankTransactionRepository bankTransactionRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuditService auditService;

    public TransactionHistoryService(BankTransactionRepository bankTransactionRepository,
                                     UserProfileRepository userProfileRepository,
                                     AuditService auditService) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.userProfileRepository = userProfileRepository;
        this.auditService = auditService;
    }

    /**
     * Retrieves paginated transaction history for the authenticated customer.
     */
    public Page<TransactionSummaryResponse> getCustomerTransactions(Jwt jwt, Pageable pageable) {
        String sub = extractSub(jwt);
        UserProfile caller = getActiveUserProfile(sub);

        return bankTransactionRepository.findByCustomerKeycloakUserId(sub, pageable)
                .map(tx -> toCustomerSummary(tx, sub));
    }

    /**
     * Retrieves specific transaction details for the authenticated customer.
     */
    public TransactionSummaryResponse getCustomerTransactionDetails(UUID id, Jwt jwt) {
        String sub = extractSub(jwt);
        getActiveUserProfile(sub);

        BankTransaction tx = bankTransactionRepository.findById(id)
                .orElseThrow(() -> {
                    auditAccessDenial(sub, id, "Customer transaction lookup not found");
                    return new AccessDeniedException("Access denied: Transaction not found or unauthorized");
                });

        boolean isSource = tx.getSourceAccount().getOwner().getKeycloakUserId().equals(sub);
        boolean isDest = tx.getDestinationAccount().getOwner().getKeycloakUserId().equals(sub);

        if (!isSource && !isDest) {
            auditAccessDenial(sub, id, "Customer attempted to access transaction of another party");
            throw new AccessDeniedException("Access denied: Transaction not found or unauthorized");
        }

        return toCustomerSummary(tx, sub);
    }

    /**
     * Retrieves paginated transaction history scoped to the staff member's assigned active branch.
     */
    public Page<TransactionSummaryResponse> getStaffTransactions(Jwt jwt, Pageable pageable) {
        String sub = extractSub(jwt);
        UserProfile staff = getActiveUserProfile(sub);
        Branch branch = getActiveBranch(staff, sub);

        return bankTransactionRepository.findByBranchId(branch.getId(), pageable)
                .map(TransactionSummaryResponse::from);
    }

    /**
     * Retrieves specific transaction details for a staff member within their branch scope.
     */
    public TransactionSummaryResponse getStaffTransactionDetails(UUID id, Jwt jwt) {
        String sub = extractSub(jwt);
        UserProfile staff = getActiveUserProfile(sub);
        Branch branch = getActiveBranch(staff, sub);

        BankTransaction tx = bankTransactionRepository.findById(id)
                .orElseThrow(() -> {
                    auditAccessDenial(sub, id, "Staff transaction lookup not found");
                    return new AccessDeniedException("Access denied: Transaction not found or unauthorized");
                });

        Branch srcBranch = tx.getSourceAccount().getOwner().getBranch();
        Branch dstBranch = tx.getDestinationAccount().getOwner().getBranch();

        boolean inBranch = (srcBranch != null && branch.getId().equals(srcBranch.getId())) ||
                           (dstBranch != null && branch.getId().equals(dstBranch.getId()));

        if (!inBranch) {
            auditAccessDenial(sub, id, "Staff attempted to access cross-branch transaction outside assigned branch");
            throw new AccessDeniedException("Access denied: Transaction not found or unauthorized");
        }

        return TransactionSummaryResponse.from(tx);
    }

    /**
     * Retrieves paginated transaction history for auditors across all accounts.
     */
    public Page<TransactionSummaryResponse> getAuditorTransactions(Pageable pageable) {
        return bankTransactionRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(TransactionSummaryResponse::from);
    }

    /**
     * Retrieves specific transaction details for an auditor.
     */
    public TransactionSummaryResponse getAuditorTransactionDetails(UUID id) {
        BankTransaction tx = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + id));
        return TransactionSummaryResponse.from(tx);
    }

    private TransactionSummaryResponse toCustomerSummary(BankTransaction tx, String sub) {
        boolean isDebit = tx.getSourceAccount().getOwner().getKeycloakUserId().equals(sub);
        String direction = isDebit ? "DEBIT" : "CREDIT";
        return TransactionSummaryResponse.from(tx, direction);
    }

    private UserProfile getActiveUserProfile(String sub) {
        UserProfile profile = userProfileRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> new AccessDeniedException("Access denied: User profile not found"));
        if (profile.getProfileStatus() != ProfileStatus.ACTIVE) {
            throw new AccessDeniedException("Access denied: User profile is not active");
        }
        return profile;
    }

    private Branch getActiveBranch(UserProfile staff, String sub) {
        Branch branch = staff.getBranch();
        if (branch == null) {
            auditAccessDenial(sub, null, "Staff member has no assigned branch");
            throw new AccessDeniedException("Access denied: Staff member has no assigned branch");
        }
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            auditAccessDenial(sub, null, "Staff assigned branch is inactive: " + branch.getBranchCode());
            throw new AccessDeniedException("Access denied: Staff assigned branch is inactive");
        }
        return branch;
    }

    private void auditAccessDenial(String sub, UUID txId, String reason) {
        auditService.logSecurityEvent(
                "TRANSACTION_HISTORY_ACCESS_DENIED",
                sub,
                null,
                "GET",
                txId != null ? "/api/transactions/" + txId : "/api/transactions",
                403,
                null,
                reason
        );
    }

    private String extractSub(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Access denied: Missing authenticated subject");
        }
        return jwt.getSubject();
    }
}

