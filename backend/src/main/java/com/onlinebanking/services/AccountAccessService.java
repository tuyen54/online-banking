package com.onlinebanking.services;

import com.onlinebanking.dto.BankAccountResponse;
import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.repositories.BankAccountRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for authorized customer bank account access.
 */
@Service
public class AccountAccessService {

    private final BankAccountRepository bankAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthorizationPolicyService authorizationPolicyService;

    public AccountAccessService(BankAccountRepository bankAccountRepository,
                                UserProfileRepository userProfileRepository,
                                AuthorizationPolicyService authorizationPolicyService) {
        this.bankAccountRepository = bankAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    /**
     * List only accounts owned by the authenticated customer.
     * The owner is derived strictly from the JWT sub, never from request parameters.
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<BankAccountResponse> listMyAccounts(Jwt jwt) {
        String sub = jwt.getSubject();
        UserProfile caller = userProfileRepository.findByKeycloakUserId(sub)
                .orElseThrow(() -> new AccessDeniedException("User profile not found"));

        if (caller.getProfileStatus() != ProfileStatus.ACTIVE) {
            throw new AccessDeniedException("User profile is not active");
        }

        return bankAccountRepository.findByOwnerKeycloakUserId(sub)
                .stream()
                .map(BankAccountResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve account details for a specific account with ABAC ownership verification.
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    public BankAccountResponse getAccountDetails(UUID accountId, Jwt jwt) {
        BankAccount account = authorizationPolicyService.assertCanAccessAccount(jwt, accountId);
        return BankAccountResponse.from(account);
    }
}
