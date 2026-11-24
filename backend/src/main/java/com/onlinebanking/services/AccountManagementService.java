package com.onlinebanking.services;

import com.onlinebanking.dto.AccountCreationRequest;
import com.onlinebanking.dto.AccountResponse;
import com.onlinebanking.dto.AdminAccountResponse;
import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.AccountType;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.repositories.BankAccountRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing Bank Account lifecycle, creation, customer access, and admin locking.
 */
@Service
public class AccountManagementService {

    private final BankAccountRepository bankAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AuditService auditService;

    public AccountManagementService(BankAccountRepository bankAccountRepository,
                                    UserProfileRepository userProfileRepository,
                                    AccountNumberGenerator accountNumberGenerator,
                                    AuditService auditService) {
        this.bankAccountRepository = bankAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.auditService = auditService;
    }

    // ─────────────────────────────────────────────────────────────
    // Customer Account Operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Customer opens a new account.
     * Owner identity is derived strictly from the verified JWT.
     * Balance is strictly initialized to zero.
     */
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountResponse openAccount(AccountCreationRequest request, Jwt customerJwt) {
        String sub = customerJwt.getSubject();
        String username = customerJwt.getClaimAsString("preferred_username");

        UserProfile profile = userProfileRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> {
                    auditService.logSecurityEvent(
                            "ACCOUNT_CREATION_DENIED",
                            sub,
                            username,
                            "POST",
                            "/api/accounts",
                            403,
                            null,
                            "Cannot open account: UserProfile not found"
                    );
                    return new AccessDeniedException("User profile not found");
                });

        if (profile.getProfileStatus() != ProfileStatus.ACTIVE) {
            auditService.logSecurityEvent(
                    "ACCOUNT_CREATION_DENIED",
                    sub,
                    username,
                    "POST",
                    "/api/accounts",
                    403,
                    null,
                    "Cannot open account: UserProfile is not ACTIVE (status=" + profile.getProfileStatus() + ")"
            );
            throw new AccessDeniedException("Cannot open account: Banking profile is not active");
        }

        // Generate cryptographically unique account number
        String accountNumber = accountNumberGenerator.generateUniqueAccountNumber(
                bankAccountRepository::existsByAccountNumber
        );

        BankAccount account = new BankAccount();
        account.setAccountNumber(accountNumber);
        account.setOwner(profile);
        account.setAccountType(request != null && request.getAccountType() != null ? request.getAccountType() : AccountType.CHECKING);
        account.setCurrency(request != null && request.getCurrency() != null && !request.getCurrency().isBlank() ? request.getCurrency().toUpperCase() : "VND");
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);
        account.setOpenedAt(LocalDateTime.now());

        BankAccount saved = bankAccountRepository.save(account);

        auditService.logSecurityEvent(
                "ACCOUNT_CREATED",
                sub,
                username,
                "POST",
                "/api/accounts",
                201,
                null,
                String.format("Account opened successfully. Type: %s, Currency: %s, Number: %s",
                        saved.getAccountType(), saved.getCurrency(), AccountNumberGenerator.mask(accountNumber))
        );

        return AccountResponse.from(saved);
    }

    /**
     * Customer lists their own accounts.
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<AccountResponse> listMyAccounts(Jwt customerJwt) {
        String sub = customerJwt.getSubject();
        UserProfile profile = userProfileRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> new AccessDeniedException("User profile not found"));

        if (profile.getProfileStatus() != ProfileStatus.ACTIVE) {
            throw new AccessDeniedException("Banking profile is not active");
        }

        return bankAccountRepository.findByOwnerKeycloakUserId(sub)
                .stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Customer views details of their own account.
     * Allowed even if account is LOCKED or FROZEN (customer can inspect status and balance).
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    public AccountResponse getMyAccountDetails(UUID accountId, Jwt customerJwt) {
        String sub = customerJwt.getSubject();
        UserProfile profile = userProfileRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> new AccessDeniedException("User profile not found"));

        if (profile.getProfileStatus() != ProfileStatus.ACTIVE) {
            throw new AccessDeniedException("Banking profile is not active");
        }

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new AccessDeniedException("Account not found or unauthorized"));

        if (!account.getOwner().getId().equals(profile.getId())) {
            auditService.logSecurityEvent(
                    "ABAC_ACCESS_DENIED",
                    sub,
                    customerJwt.getClaimAsString("preferred_username"),
                    "GET",
                    "/api/accounts/" + accountId,
                    403,
                    null,
                    "Customer attempted to access account owned by another profile"
            );
            throw new AccessDeniedException("Account not found or unauthorized");
        }

        return AccountResponse.from(account);
    }

    // ─────────────────────────────────────────────────────────────
    // Administrative Account Management (ADMIN role only)
    // ─────────────────────────────────────────────────────────────

    /**
     * Administrative search & lookup of accounts.
     * Never returns balances!
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminAccountResponse> searchAccounts(AccountStatus status, AccountType type, Pageable pageable) {
        Page<BankAccount> accounts;

        if (status != null && type != null) {
            accounts = bankAccountRepository.findByAccountStatusAndAccountType(status, type, pageable);
        } else if (status != null) {
            accounts = bankAccountRepository.findByAccountStatus(status, pageable);
        } else if (type != null) {
            accounts = bankAccountRepository.findByAccountType(type, pageable);
        } else {
            accounts = bankAccountRepository.findAll(pageable);
        }

        return accounts.map(AdminAccountResponse::from);
    }

    /**
     * Temporarily lock an ACTIVE account.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminAccountResponse lockAccount(UUID accountId, String reason, Jwt adminJwt) {
        validateReason(reason);
        String adminSub = adminJwt.getSubject();
        String adminUsername = adminJwt.getClaimAsString("preferred_username");

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            auditService.logSecurityEvent(
                    "ACCOUNT_STATUS_CHANGE_DENIED",
                    adminSub,
                    adminUsername,
                    "POST",
                    "/api/admin/accounts/" + accountId + "/lock",
                    400,
                    null,
                    "Cannot lock account: Current status is " + account.getAccountStatus() + " (expected ACTIVE)"
            );
            throw new IllegalStateException("Cannot lock account: current status is " + account.getAccountStatus() + " (expected ACTIVE)");
        }

        account.setAccountStatus(AccountStatus.LOCKED);
        BankAccount updated = bankAccountRepository.save(account);

        auditService.logSecurityEvent(
                "ACCOUNT_LOCKED",
                adminSub,
                adminUsername,
                "POST",
                "/api/admin/accounts/" + accountId + "/lock",
                200,
                null,
                String.format("Account [%s] locked by admin [%s]. Reason: %s",
                        accountId, adminSub, reason.trim())
        );

        return AdminAccountResponse.from(updated);
    }

    /**
     * Unlock a LOCKED account.
     * Cannot unlock CLOSED accounts.
     */
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AdminAccountResponse unlockAccount(UUID accountId, String reason, Jwt adminJwt) {
        validateReason(reason);
        String adminSub = adminJwt.getSubject();
        String adminUsername = adminJwt.getClaimAsString("preferred_username");

        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found with ID: " + accountId));

        if (account.getAccountStatus() == AccountStatus.CLOSED) {
            auditService.logSecurityEvent(
                    "ACCOUNT_STATUS_CHANGE_DENIED",
                    adminSub,
                    adminUsername,
                    "POST",
                    "/api/admin/accounts/" + accountId + "/unlock",
                    400,
                    null,
                    "Cannot unlock permanently CLOSED account: " + accountId
            );
            throw new IllegalStateException("Cannot unlock a permanently CLOSED account");
        }

        if (account.getAccountStatus() != AccountStatus.LOCKED) {
            auditService.logSecurityEvent(
                    "ACCOUNT_STATUS_CHANGE_DENIED",
                    adminSub,
                    adminUsername,
                    "POST",
                    "/api/admin/accounts/" + accountId + "/unlock",
                    400,
                    null,
                    "Cannot unlock account: Current status is " + account.getAccountStatus() + " (expected LOCKED)"
            );
            throw new IllegalStateException("Cannot unlock account: current status is " + account.getAccountStatus() + " (expected LOCKED)");
        }

        account.setAccountStatus(AccountStatus.ACTIVE);
        BankAccount updated = bankAccountRepository.save(account);

        auditService.logSecurityEvent(
                "ACCOUNT_UNLOCKED",
                adminSub,
                adminUsername,
                "POST",
                "/api/admin/accounts/" + accountId + "/unlock",
                200,
                null,
                String.format("Account [%s] unlocked by admin [%s]. Reason: %s",
                        accountId, adminSub, reason.trim())
        );

        return AdminAccountResponse.from(updated);
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Administrative reason is mandatory");
        }
    }
}

