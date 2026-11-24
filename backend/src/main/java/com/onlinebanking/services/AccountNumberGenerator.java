package com.onlinebanking.services;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * Cryptographically secure account number generator.
 *
 * <p>Generates non-predictable 12-digit account numbers formatted as:
 * {@code 100} + 9 random digits.
 *
 * <p>Includes collision handling with bounded retry.
 */
@Component
public class AccountNumberGenerator {

    private static final String DEFAULT_PREFIX = "100";
    private static final int RANDOM_DIGITS_COUNT = 9;
    private static final int DEFAULT_MAX_RETRIES = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a raw candidate account number.
     */
    public String generateCandidate() {
        StringBuilder sb = new StringBuilder(DEFAULT_PREFIX);
        for (int i = 0; i < RANDOM_DIGITS_COUNT; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Generate a unique account number using collision check predicate.
     *
     * @param existsPredicate Predicate returning true if account number already exists
     * @return unique generated account number
     * @throws IllegalStateException if collisions persist past max retries
     */
    public String generateUniqueAccountNumber(Predicate<String> existsPredicate) {
        return generateUniqueAccountNumber(existsPredicate, DEFAULT_MAX_RETRIES);
    }

    /**
     * Generate unique account number with custom retry bound.
     */
    public String generateUniqueAccountNumber(Predicate<String> existsPredicate, int maxRetries) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            String candidate = generateCandidate();
            if (!existsPredicate.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique account number after " + maxRetries + " attempts");
    }

    /**
     * Masks account number for logging and safe API responses.
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

