package com.onlinebanking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.services.AccountNumberGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Minimal account metadata for administrative lookup.
 *
 * <p>Security rule: Balance is NEVER exposed to administrators via this endpoint.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminAccountResponse {

    private UUID id;
    private String maskedAccountNumber;
    private String ownerUsername;
    private String ownerKeycloakUserId;
    private String accountType;
    private String accountStatus;
    private String currency;
    private LocalDateTime openedAt;

    public static AdminAccountResponse from(BankAccount account) {
        String username = account.getOwner() != null ? account.getOwner().getUsername() : "UNKNOWN";
        String keycloakUserId = account.getOwner() != null ? account.getOwner().getKeycloakUserId() : "UNKNOWN";

        return AdminAccountResponse.builder()
                .id(account.getId())
                .maskedAccountNumber(AccountNumberGenerator.mask(account.getAccountNumber()))
                .ownerUsername(username)
                .ownerKeycloakUserId(keycloakUserId)
                .accountType(account.getAccountType().name())
                .accountStatus(account.getAccountStatus().name())
                .currency(account.getCurrency())
                .openedAt(account.getOpenedAt())
                .build();
    }
}

