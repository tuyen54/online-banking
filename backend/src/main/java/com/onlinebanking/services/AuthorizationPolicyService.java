package com.onlinebanking.services;

import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.entities.Branch;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.BranchStatus;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.repositories.BankAccountRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized Attribute-Based Access Control (ABAC) Authorization Service.
 *
 * <p>Enforces resource-level authorization decisions based on database-backed attributes
 * (user status, branch assignment, branch status, account ownership, and account status).
 *
 * <p>Separates authorization decisions from business logic.
 */
@Service
public class AuthorizationPolicyService {

    private final UserProfileRepository userProfileRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AuditService auditService;

    public AuthorizationPolicyService(UserProfileRepository userProfileRepository,
                                      BankAccountRepository bankAccountRepository,
                                      AuditService auditService) {
        this.userProfileRepository = userProfileRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.auditService = auditService;
    }

    /**
     * Asserts that the authenticated user can access the target customer profile.
     *
     * <p>Rules:
     * <ul>
     *   <li>CUSTOMER: Can only access their own UserProfile. Caller profile must be ACTIVE.</li>
     *   <li>STAFF: Can access customer profiles in their assigned ACTIVE branch only.
     *       Staff profile must be ACTIVE, assigned branch must be ACTIVE, target customer must be
     *       ACTIVE and assigned to the same ACTIVE branch.</li>
     *   <li>ADMIN / AUDITOR: Denied (no direct customer profile access without explicit policy).</li>
     * </ul>
     *
     * @param jwt               verified JWT of the caller
     * @param customerProfileId target customer profile UUID
     * @return the authorized UserProfile
     * @throws AccessDeniedException if authorization fails
     */
    public UserProfile assertCanAccessCustomerProfile(Jwt jwt, UUID customerProfileId) {
        String sub = extractSub(jwt);
        List<String> roles = extractRealmRoles(jwt);

        UserProfile callerProfile = userProfileRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> {
                    auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                            "Caller UserProfile not found for sub: " + sub);
                    return new AccessDeniedException("Access denied: User profile not found");
                });

        if (callerProfile.getProfileStatus() != ProfileStatus.ACTIVE) {
            auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                    "Caller profile is not ACTIVE: status=" + callerProfile.getProfileStatus());
            throw new AccessDeniedException("Access denied: Your banking profile is not active");
        }

        UserProfile targetCustomer = userProfileRepository.findById(customerProfileId)
                .orElseThrow(() -> {
                    // Resource existence disclosure protection (BOLA): log detail internally, throw AccessDeniedException
                    auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                            "Target customer profile not found: " + customerProfileId);
                    return new AccessDeniedException("Access denied: Resource not found or unauthorized");
                });

        // 1. CUSTOMER Rule
        if (roles.contains("customer")) {
            if (!callerProfile.getId().equals(targetCustomer.getId())) {
                auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                        String.format("Customer [%s] attempted to access profile of customer [%s]",
                                callerProfile.getId(), targetCustomer.getId()));
                throw new AccessDeniedException("Access denied: You can only access your own profile");
            }
            return targetCustomer;
        }

        // 2. STAFF Rule
        if (roles.contains("staff")) {
            Branch staffBranch = callerProfile.getBranch();
            if (staffBranch == null) {
                auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                        "Staff member has no assigned branch");
                throw new AccessDeniedException("Access denied: Staff member has no assigned branch");
            }

            if (staffBranch.getStatus() != BranchStatus.ACTIVE) {
                auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                        "Staff assigned branch is inactive: " + staffBranch.getBranchCode());
                throw new AccessDeniedException("Access denied: Staff assigned branch is inactive");
            }

            if (targetCustomer.getProfileStatus() != ProfileStatus.ACTIVE) {
                auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                        "Target customer profile is not ACTIVE: " + targetCustomer.getProfileStatus());
                throw new AccessDeniedException("Access denied: Target customer profile is not active");
            }

            Branch customerBranch = targetCustomer.getBranch();
            if (customerBranch == null) {
                auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                        "Target customer has no assigned branch");
                throw new AccessDeniedException("Access denied: Customer has no assigned branch");
            }

            if (!staffBranch.getId().equals(customerBranch.getId())) {
                auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                        String.format("Staff branch [%s] does not match customer branch [%s]",
                                staffBranch.getBranchCode(), customerBranch.getBranchCode()));
                throw new AccessDeniedException("Access denied: Staff can only access customers within their assigned branch");
            }

            return targetCustomer;
        }

        // 3. ADMIN / AUDITOR / other roles
        auditDenial(sub, "CUSTOMER_PROFILE", customerProfileId.toString(),
                "Role not authorized for customer profile access: " + roles);
        throw new AccessDeniedException("Access denied: Insufficient permission to view customer profiles");
    }

    /**
     * Asserts that the authenticated user can access the specified bank account.
     *
     * <p>Rules:
     * <ul>
     *   <li>CUSTOMER: Only their own account. Caller profile must be ACTIVE.</li>
     *   <li>STAFF / ADMIN / AUDITOR: Denied on customer account endpoints.</li>
     * </ul>
     */
    public BankAccount assertCanAccessAccount(Jwt jwt, UUID accountId) {
        String sub = extractSub(jwt);
        List<String> roles = extractRealmRoles(jwt);

        if (!roles.contains("customer")) {
            auditDenial(sub, "BANK_ACCOUNT", accountId.toString(),
                    "Non-customer role attempted to access customer account: " + roles);
            throw new AccessDeniedException("Access denied: Only customers can access their personal accounts");
        }

        UserProfile callerProfile = userProfileRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> {
                    auditDenial(sub, "BANK_ACCOUNT", accountId.toString(),
                            "Caller UserProfile not found for sub: " + sub);
                    return new AccessDeniedException("Access denied: User profile not found");
                });

        if (callerProfile.getProfileStatus() != ProfileStatus.ACTIVE) {
            auditDenial(sub, "BANK_ACCOUNT", accountId.toString(),
                    "Caller profile is not ACTIVE: " + callerProfile.getProfileStatus());
            throw new AccessDeniedException("Access denied: Your banking profile is not active");
        }

        // Secure scoped lookup
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> {
                    auditDenial(sub, "BANK_ACCOUNT", accountId.toString(),
                            "Account not found: " + accountId);
                    return new AccessDeniedException("Access denied: Account not found or unauthorized");
                });

        if (!account.getOwner().getId().equals(callerProfile.getId())) {
            auditDenial(sub, "BANK_ACCOUNT", accountId.toString(),
                    String.format("Customer [%s] attempted to access account [%s] owned by [%s]",
                            callerProfile.getId(), accountId, account.getOwner().getId()));
            throw new AccessDeniedException("Access denied: Account not found or unauthorized");
        }

        return account;
    }

    /**
     * Asserts that a customer owns the source account for financial operations (such as future transfers).
     *
     * <p>Requirements:
     * <ul>
     *   <li>Account must exist and belong to the authenticated subject.</li>
     *   <li>Owner UserProfile must be ACTIVE.</li>
     *   <li>Account must be in ACTIVE status (FROZEN/CLOSED rejected).</li>
     * </ul>
     */
    public BankAccount assertCustomerOwnsAccount(UUID accountId, String authenticatedSubject) {
        if (authenticatedSubject == null || authenticatedSubject.isBlank()) {
            throw new AccessDeniedException("Access denied: Unauthenticated request");
        }

        BankAccount account = bankAccountRepository.findByIdAndOwnerKeycloakUserId(accountId, authenticatedSubject)
                .orElseThrow(() -> {
                    auditDenial(authenticatedSubject, "SOURCE_ACCOUNT_OWNERSHIP", accountId.toString(),
                            "Customer does not own requested account or account does not exist");
                    return new AccessDeniedException("Access denied: Account not found or unauthorized");
                });

        UserProfile owner = account.getOwner();
        if (owner.getProfileStatus() != ProfileStatus.ACTIVE) {
            auditDenial(authenticatedSubject, "SOURCE_ACCOUNT_OWNERSHIP", accountId.toString(),
                    "Owner profile is not ACTIVE: " + owner.getProfileStatus());
            throw new AccessDeniedException("Access denied: Customer profile is inactive or suspended");
        }

        if (account.getAccountStatus() != AccountStatus.ACTIVE) {
            auditDenial(authenticatedSubject, "SOURCE_ACCOUNT_OWNERSHIP", accountId.toString(),
                    "Account is not in ACTIVE state: " + account.getAccountStatus());
            throw new IllegalStateException("Account is not active: " + account.getAccountStatus());
        }

        return account;
    }

    public boolean canAccessCustomerProfile(Jwt jwt, UUID customerProfileId) {
        try {
            assertCanAccessCustomerProfile(jwt, customerProfileId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canAccessAccount(Jwt jwt, UUID accountId) {
        try {
            assertCanAccessAccount(jwt, accountId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractSub(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AccessDeniedException("Access denied: Missing authenticated subject");
        }
        return jwt.getSubject();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRealmRoles(Jwt jwt) {
        if (jwt == null) return Collections.emptyList();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) return Collections.emptyList();
        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> coll)) return Collections.emptyList();

        return coll.stream()
                .filter(r -> r instanceof String)
                .map(r -> ((String) r).toLowerCase())
                .toList();
    }

    private void auditDenial(String actorSub, String resourceType, String resourceId, String reason) {
        auditService.logSecurityEvent(
                "ABAC_ACCESS_DENIED",
                actorSub,
                null,
                "GET",
                resourceType + "/" + resourceId,
                403,
                null,
                reason
        );
    }
}
