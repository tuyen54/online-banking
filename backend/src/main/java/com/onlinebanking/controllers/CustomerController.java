package com.onlinebanking.controllers;

import com.onlinebanking.dto.CustomerProfileResponse;
import com.onlinebanking.services.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Customer profile endpoints.
 *
 * <p>Enforces RBAC + ABAC:
 * <ul>
 *   <li>{@code GET /api/customers/{userProfileId}}: CUSTOMER can only access their own profile.
 *       STAFF can only access customer profiles within their assigned active branch.</li>
 *   <li>{@code GET /api/customers/me}: Preserved for backward compatibility.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * Retrieve customer profile by ID with resource-level ABAC validation.
     */
    @GetMapping("/{userProfileId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF')")
    public ResponseEntity<CustomerProfileResponse> getCustomerProfile(
            @PathVariable UUID userProfileId,
            JwtAuthenticationToken authentication) {
        CustomerProfileResponse response = customerService.getCustomerProfile(userProfileId, authentication.getToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the authenticated customer's profile information
     * extracted from the Keycloak-issued JWT (Task 01 compatibility).
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Map<String, String>> getMyProfile(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");

        return ResponseEntity.ok(Map.of(
                "userId", userId != null ? userId : "",
                "username", username != null ? username : "",
                "email", email != null ? email : "",
                "message", "Customer profile endpoint — authenticated"
        ));
    }
}
