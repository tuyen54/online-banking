package com.onlinebanking.services;

import com.onlinebanking.dto.CustomerProfileResponse;
import com.onlinebanking.entities.UserProfile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for authorized customer profile access operations.
 */
@Service
public class CustomerService {

    private final AuthorizationPolicyService authorizationPolicyService;

    public CustomerService(AuthorizationPolicyService authorizationPolicyService) {
        this.authorizationPolicyService = authorizationPolicyService;
    }

    /**
     * Retrieve a customer profile with RBAC and ABAC authorization enforcement.
     *
     * @param customerProfileId target UserProfile UUID
     * @param jwt               verified JWT of the caller
     * @return safe CustomerProfileResponse DTO
     */
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    public CustomerProfileResponse getCustomerProfile(UUID customerProfileId, Jwt jwt) {
        UserProfile profile = authorizationPolicyService.assertCanAccessCustomerProfile(jwt, customerProfileId);
        return CustomerProfileResponse.from(profile);
    }
}
