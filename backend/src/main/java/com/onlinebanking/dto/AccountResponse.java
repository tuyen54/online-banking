package com.onlinebanking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.services.AccountNumberGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Full account details response for the authenticated account owner.
 *
 * <p>Includes available balance and masked account number. Full account number is never exposed.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse {

    private UUID id;
    private String maskedAccountNumber;
    private String accountType;
    private String accountStatus;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime openedAt;

    public static AccountResponse from(BankAccount account) {
        return AccountResponse.builder()
                .id(account.getId())
                .maskedAccountNumber(AccountNumberGenerator.mask(account.getAccountNumber()))
                .accountType(account.getAccountType().name())
                .accountStatus(account.getAccountStatus().name())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .openedAt(account.getOpenedAt())
                .build();
    }
}

