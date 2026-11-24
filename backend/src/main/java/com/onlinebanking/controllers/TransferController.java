package com.onlinebanking.controllers;

import com.onlinebanking.dto.TransferRequest;
import com.onlinebanking.dto.TransferResponse;
import com.onlinebanking.services.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transfer endpoints — CUSTOMER role only.
 *
 * <p>ADMIN, STAFF, and AUDITOR are strictly prohibited from executing transfers.
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Executes an internal transfer between active accounts.
     * Enforces Idempotency-Key header, ABAC account ownership, and pessimistic locking.
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TransferRequest request) {
        TransferResponse response = transferService.executeTransfer(jwt, idempotencyKey, request);
        return ResponseEntity.ok(response);
    }
}
