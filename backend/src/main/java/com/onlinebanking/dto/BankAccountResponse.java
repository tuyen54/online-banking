package com.onlinebanking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinebanking.entities.BankAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Safe DTO for bank account representation.
 *
 * <p>Security rule: Full account number is NEVER exposed in the API response.
 * It is always masked (e.g., "******1234").
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BankAccountResponse {

    private UUID id;
    private String maskedAccountNumber;
    private String accountType;
    private String accountStatus;
    private String currency;
    private LocalDateTime openedAt;

    public static BankAccountResponse from(BankAccount account) {
        return BankAccountResponse.builder()
                .id(account.getId())
                .maskedAccountNumber(mask(account.getAccountNumber()))
                .accountType(account.getAccountType().name())
                .accountStatus(account.getAccountStatus().name())
                .currency(account.getCurrency())
                .openedAt(account.getOpenedAt())
                .build();
    }

    /**
     * Masks the account number, showing at most the last 4 characters.
     */
    public static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "******";
        }
        int len = accountNumber.length();
        if (len <= 4) {
            return "******" + accountNumber;
        }
        return "******" + accountNumber.substring(len - 4);
    }
}

