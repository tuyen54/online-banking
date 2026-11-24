package com.onlinebanking.controllers;

import com.onlinebanking.dto.AccountCreationRequest;
import com.onlinebanking.dto.AccountResponse;
import com.onlinebanking.services.AccountManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller for customer bank account lifecycle and access.
 *
 * <p>Enforces strict customer-only ownership:
 * <ul>
 *   <li>Only authenticated CUSTOMER can create accounts (with zero initial balance).</li>
 *   <li>Customers can only list and view their own accounts.</li>
 *   <li>STAFF, ADMIN, and AUDITOR are denied on these endpoints.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountManagementService accountManagementService;

    public AccountController(AccountManagementService accountManagementService) {
        this.accountManagementService = accountManagementService;
    }

    /**
     * Customer opens a new bank account.
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AccountResponse> openAccount(
            @RequestBody(required = false) AccountCreationRequest request,
            JwtAuthenticationToken authentication) {
        AccountResponse response = accountManagementService.openAccount(request, authentication.getToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all accounts owned by the authenticated customer.
     */
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(JwtAuthenticationToken authentication) {
        List<AccountResponse> accounts = accountManagementService.listMyAccounts(authentication.getToken());
        return ResponseEntity.ok(accounts);
    }

    /**
     * Get details for a specific bank account with ABAC ownership verification.
     */
    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AccountResponse> getAccountDetails(
            @PathVariable UUID accountId,
            JwtAuthenticationToken authentication) {
        AccountResponse response = accountManagementService.getMyAccountDetails(accountId, authentication.getToken());
        return ResponseEntity.ok(response);
    }
}
