package com.onlinebanking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinebanking.entities.BankTransaction;
import com.onlinebanking.services.AccountNumberGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Safe transaction history DTO with masked account numbers and role-appropriate details.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionSummaryResponse {

    private UUID id;
    private String transactionReference;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String description;
    private LocalDateTime timestamp;
    private String direction;

    public static TransactionSummaryResponse from(BankTransaction tx) {
        return from(tx, null);
    }

    public static TransactionSummaryResponse from(BankTransaction tx, String direction) {
        return TransactionSummaryResponse.builder()
                .id(tx.getId())
                .transactionReference(tx.getTransactionReference())
                .status(tx.getStatus().name())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .sourceAccountNumber(AccountNumberGenerator.mask(tx.getSourceAccount().getAccountNumber()))
                .destinationAccountNumber(AccountNumberGenerator.mask(tx.getDestinationAccount().getAccountNumber()))
                .description(tx.getDescription())
                .timestamp(tx.getCompletedAt() != null ? tx.getCompletedAt() : tx.getCreatedAt())
                .direction(direction)
                .build();
    }
}

