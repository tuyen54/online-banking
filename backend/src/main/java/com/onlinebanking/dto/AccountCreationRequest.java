package com.onlinebanking.dto;

import com.onlinebanking.entities.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for customer account creation.
 *
 * <p>Security rule: Does not accept balance, ownerId, accountNumber, or status.
 * All sensitive attributes are derived and managed exclusively by backend services.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreationRequest {

    /** Preferred account type (CHECKING or SAVINGS). Defaults to CHECKING if null. */
    private AccountType accountType = AccountType.CHECKING;

    /** 3-character ISO currency code. Defaults to "VND" if null. */
    private String currency = "VND";
}

