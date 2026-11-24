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

/**
 * Response payload returned upon successful transfer execution or idempotent replay.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferResponse {

    private String transactionReference;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private String description;
    private LocalDateTime completedAt;

    public static TransferResponse from(BankTransaction tx) {
        return TransferResponse.builder()
                .transactionReference(tx.getTransactionReference())
                .status(tx.getStatus().name())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .sourceAccountNumber(AccountNumberGenerator.mask(tx.getSourceAccount().getAccountNumber()))
                .destinationAccountNumber(AccountNumberGenerator.mask(tx.getDestinationAccount().getAccountNumber()))
                .description(tx.getDescription())
                .completedAt(tx.getCompletedAt() != null ? tx.getCompletedAt() : tx.getCreatedAt())
                .build();
    }
}

