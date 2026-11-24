package com.onlinebanking.entities.enums;

/**
 * Operational lifecycle status of a bank account.
 *
 * <p>State transitions:
 * <ul>
 *   <li>ACTIVE &rarr; LOCKED (administrative lock)</li>
 *   <li>LOCKED &rarr; ACTIVE (administrative unlock)</li>
 *   <li>ACTIVE &rarr; FROZEN (suspicious activity / legal hold)</li>
 *   <li>FROZEN &rarr; ACTIVE (administrative review)</li>
 *   <li>ACTIVE / LOCKED / FROZEN &rarr; CLOSED (terminal state)</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    FROZEN,
    CLOSED
}
