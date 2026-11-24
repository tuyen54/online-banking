package com.onlinebanking.controllers;

import com.onlinebanking.dto.TransactionSummaryResponse;
import com.onlinebanking.services.TransactionHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only transaction history endpoints.
 *
 * <p>Supported roles:
 * <ul>
 *   <li>CUSTOMER: can only view their own incoming and outgoing transactions.</li>
 *   <li>STAFF: can only view transactions within their assigned branch.</li>
 *   <li>AUDITOR: full read-only chronological access to all transactions.</li>
 *   <li>ADMIN: denied access (financial transaction history access restricted).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionHistoryService transactionHistoryService;

    public TransactionController(TransactionHistoryService transactionHistoryService) {
        this.transactionHistoryService = transactionHistoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'AUDITOR') and !hasRole('ADMIN')")
    public ResponseEntity<Page<TransactionSummaryResponse>> getTransactions(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (authorities.contains("ROLE_CUSTOMER")) {
            return ResponseEntity.ok(transactionHistoryService.getCustomerTransactions(jwt, pageable));
        } else if (authorities.contains("ROLE_STAFF")) {
            return ResponseEntity.ok(transactionHistoryService.getStaffTransactions(jwt, pageable));
        } else if (authorities.contains("ROLE_AUDITOR")) {
            return ResponseEntity.ok(transactionHistoryService.getAuditorTransactions(pageable));
        }

        return ResponseEntity.status(403).build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'AUDITOR') and !hasRole('ADMIN')")
    public ResponseEntity<TransactionSummaryResponse> getTransactionDetails(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (authorities.contains("ROLE_CUSTOMER")) {
            return ResponseEntity.ok(transactionHistoryService.getCustomerTransactionDetails(id, jwt));
        } else if (authorities.contains("ROLE_STAFF")) {
            return ResponseEntity.ok(transactionHistoryService.getStaffTransactionDetails(id, jwt));
        } else if (authorities.contains("ROLE_AUDITOR")) {
            return ResponseEntity.ok(transactionHistoryService.getAuditorTransactionDetails(id));
        }

        return ResponseEntity.status(403).build();
    }
}

