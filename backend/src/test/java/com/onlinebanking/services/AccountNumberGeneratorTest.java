package com.onlinebanking.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNumberGeneratorTest {

    private final AccountNumberGenerator generator = new AccountNumberGenerator();

    @Test
    @DisplayName("Generated account numbers follow 12-digit format starting with prefix 100")
    void shouldFollowFormat() {
        String num = generator.generateCandidate();
        assertThat(num).hasSize(12);
        assertThat(num).startsWith("100");
        assertThat(num).containsOnlyDigits();
    }

    @Test
    @DisplayName("Generated account numbers are distinct (randomness test)")
    void shouldGenerateDistinctNumbers() {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            set.add(generator.generateCandidate());
        }
        assertThat(set).hasSize(500);
    }

    @Test
    @DisplayName("Handles collisions with bounded retries")
    void shouldHandleCollision() {
        AtomicInteger attempts = new AtomicInteger(0);
        // Simulate collision for the first 2 attempts, then success
        String uniqueNum = generator.generateUniqueAccountNumber(candidate -> attempts.getAndIncrement() < 2, 5);

        assertThat(uniqueNum).isNotNull().hasSize(12);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("Throws IllegalStateException when collision retries are exhausted")
    void shouldFailWhenRetriesExhausted() {
        assertThatThrownBy(() -> generator.generateUniqueAccountNumber(candidate -> true, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate a unique account number after 3 attempts");
    }

    @Test
    @DisplayName("Masking utility masks all but last 4 digits")
    void shouldMaskCorrectly() {
        assertThat(AccountNumberGenerator.mask("100123456789")).isEqualTo("******6789");
        assertThat(AccountNumberGenerator.mask("1234")).isEqualTo("******1234");
        assertThat(AccountNumberGenerator.mask("12")).isEqualTo("******12");
        assertThat(AccountNumberGenerator.mask(null)).isEqualTo("******");
        assertThat(AccountNumberGenerator.mask("")).isEqualTo("******");
    }
}

