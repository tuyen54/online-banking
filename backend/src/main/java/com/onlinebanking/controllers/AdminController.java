package com.onlinebanking.controllers;

import com.onlinebanking.dto.AccountStatusChangeRequest;
import com.onlinebanking.dto.AdminAccountResponse;
import com.onlinebanking.dto.BranchAssignmentRequest;
import com.onlinebanking.dto.BranchResponse;
import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.AccountType;
import com.onlinebanking.services.AccountManagementService;
import com.onlinebanking.services.BranchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Administrative endpoints — accessible only to users with the ADMIN role.
 *
 * <p>ADMIN can manage user accounts and security statuses (lock/unlock),
 * but can NEVER perform financial transactions or modify account balances.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BranchService branchService;
    private final AccountManagementService accountManagementService;

    public AdminController(BranchService branchService,
                           AccountManagementService accountManagementService) {
        this.branchService = branchService;
        this.accountManagementService = accountManagementService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> listUsers() {
        return ResponseEntity.ok(Map.of(
                "message", "Admin user management endpoint — to be implemented"
        ));
    }

    @PostMapping("/users/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> unlockUser(@PathVariable String userId) {
        return ResponseEntity.ok(Map.of(
                "message", "Account unlock endpoint — to be implemented",
                "targetUserId", userId
        ));
    }

    @GetMapping("/security-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> viewSecurityLogs() {
        return ResponseEntity.ok(Map.of(
                "message", "Security logs endpoint — to be implemented"
        ));
    }

    /**
     * Administrative branch assignment for a UserProfile.
     */
    @PatchMapping("/users/{userProfileId}/branch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BranchResponse> assignBranch(
            @PathVariable UUID userProfileId,
            @Valid @RequestBody BranchAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        BranchResponse response = branchService.assignBranch(userProfileId, request.getBranchId(), jwt);
        return ResponseEntity.ok(response);
    }

    /**
     * Administrative account lookup (masked numbers, NO balances).
     */
    @GetMapping("/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminAccountResponse>> searchAccounts(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) AccountType type,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AdminAccountResponse> result = accountManagementService.searchAccounts(status, type, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Administrative account locking (requires mandatory reason).
     */
    @PostMapping("/accounts/{accountId}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminAccountResponse> lockAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountStatusChangeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AdminAccountResponse response = accountManagementService.lockAccount(accountId, request.getReason(), jwt);
        return ResponseEntity.ok(response);
    }

    /**
     * Administrative account unlocking (requires mandatory reason).
     */
    @PostMapping("/accounts/{accountId}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminAccountResponse> unlockAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody AccountStatusChangeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        AdminAccountResponse response = accountManagementService.unlockAccount(accountId, request.getReason(), jwt);
        return ResponseEntity.ok(response);
    }
}
