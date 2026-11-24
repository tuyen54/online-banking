package com.onlinebanking.security;

import com.onlinebanking.dto.TransferRequest;
import com.onlinebanking.dto.TransferResponse;
import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.entities.Branch;
import com.onlinebanking.entities.LedgerEntry;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.AccountType;
import com.onlinebanking.entities.enums.BranchStatus;
import com.onlinebanking.entities.enums.LedgerEntryType;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.repositories.AuditLogRepository;
import com.onlinebanking.repositories.BankAccountRepository;
import com.onlinebanking.repositories.BankTransactionRepository;
import com.onlinebanking.repositories.BranchRepository;
import com.onlinebanking.repositories.LedgerEntryRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import com.onlinebanking.services.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransferConcurrencyAndDeadlockTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private TransferService transferService;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private BankTransactionRepository bankTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private UserProfile userA;
    private UserProfile userB;
    private BankAccount accountA;
    private BankAccount accountB;

    private Jwt jwtA;
    private Jwt jwtB;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        bankTransactionRepository.deleteAll();
        auditLogRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();
        branchRepository.deleteAll();

        Branch branch = new Branch();
        branch.setBranchCode("BR-CONCUR");
        branch.setBranchName("Concurrency Branch");
        branch.setStatus(BranchStatus.ACTIVE);
        branch = branchRepository.save(branch);

        userA = new UserProfile();
        userA.setKeycloakUserId("concur-user-a");
        userA.setUsername("user_a");
        userA.setEmail("usera@test.com");
        userA.setFirstName("Alice");
        userA.setLastName("Concur");
        userA.setProfileStatus(ProfileStatus.ACTIVE);
        userA.setBranch(branch);
        userA = userProfileRepository.save(userA);

        userB = new UserProfile();
        userB.setKeycloakUserId("concur-user-b");
        userB.setUsername("user_b");
        userB.setEmail("userb@test.com");
        userB.setFirstName("Bob");
        userB.setLastName("Concur");
        userB.setProfileStatus(ProfileStatus.ACTIVE);
        userB.setBranch(branch);
        userB = userProfileRepository.save(userB);

        accountA = new BankAccount();
        accountA.setAccountNumber("100999999001");
        accountA.setAccountType(AccountType.CHECKING);
        accountA.setAccountStatus(AccountStatus.ACTIVE);
        accountA.setBalance(new BigDecimal("1000000.00")); // 1,000,000 VND
        accountA.setCurrency("VND");
        accountA.setOwner(userA);
        accountA = bankAccountRepository.save(accountA);

        accountB = new BankAccount();
        accountB.setAccountNumber("100999999002");
        accountB.setAccountType(AccountType.CHECKING);
        accountB.setAccountStatus(AccountStatus.ACTIVE);
        accountB.setBalance(new BigDecimal("1000000.00")); // 1,000,000 VND
        accountB.setCurrency("VND");
        accountB.setOwner(userB);
        accountB = bankAccountRepository.save(accountB);

        jwtA = createJwt("concur-user-a", "user_a");
        jwtB = createJwt("concur-user-b", "user_b");
    }

    private Jwt createJwt(String sub, String username) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", sub,
                        "preferred_username", username,
                        "realm_access", Map.of("roles", List.of("customer"))
                )
        );
    }

    @Test
    @DisplayName("Prevent overspending: 10 concurrent requests for 200,000 VND against 1,000,000 VND balance allows exactly 5")
    void testConcurrentOverspendingPrevention() throws Exception {
        int threads = 10;
        BigDecimal transferAmount = new BigDecimal("200000.00");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String key = "overdraft-key-" + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TransferRequest request = TransferRequest.builder()
                            .sourceAccountId(accountA.getId())
                            .destinationAccountNumber(accountB.getAccountNumber())
                            .amount(transferAmount)
                            .description("Concurrent transfer")
                            .build();
                    transferService.executeTransfer(jwtA, key, request);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().contains("Insufficient balance")) {
                        failCount.incrementAndGet();
                    } else {
                        System.err.println("IllegalArgumentException: " + e.getMessage());
                    }
                } catch (Exception e) {
                    System.err.println("Exception: " + e.getClass().getName() + " -> " + e.getMessage());
                    failCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly 5 should succeed (5 * 200,000 = 1,000,000)
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        // Account A balance must be exactly 0.00
        BankAccount finalA = bankAccountRepository.findById(accountA.getId()).orElseThrow();
        assertThat(finalA.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        // Account B balance must be exactly 2,000,000.00
        BankAccount finalB = bankAccountRepository.findById(accountB.getId()).orElseThrow();
        assertThat(finalB.getBalance()).isEqualByComparingTo(new BigDecimal("2000000.00"));

        // Verify all ledger entries maintain SUM(DEBIT) == SUM(CREDIT)
        List<LedgerEntry> allLedgerEntries = ledgerEntryRepository.findAll();
        BigDecimal totalDebit = allLedgerEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = allLedgerEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDebit).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(totalCredit).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(totalDebit).isEqualByComparingTo(totalCredit);
    }

    @Test
    @DisplayName("Prevent deadlocks: Simultaneous opposite-direction transfers (A->B and B->A) succeed safely")
    void testBidirectionalTransferDeadlockFreedom() throws Exception {
        int threadsPerDirection = 4;
        int totalThreads = threadsPerDirection * 2;
        BigDecimal transferAmount = new BigDecimal("50000.00");

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();

        // Direction 1: A -> B
        for (int i = 0; i < threadsPerDirection; i++) {
            final String key = "bidirectional-a-to-b-" + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TransferRequest request = TransferRequest.builder()
                            .sourceAccountId(accountA.getId())
                            .destinationAccountNumber(accountB.getAccountNumber())
                            .amount(transferAmount)
                            .description("A to B")
                            .build();
                    transferService.executeTransfer(jwtA, key, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected failure
                }
            }));
        }

        // Direction 2: B -> A
        for (int i = 0; i < threadsPerDirection; i++) {
            final String key = "bidirectional-b-to-a-" + i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TransferRequest request = TransferRequest.builder()
                            .sourceAccountId(accountB.getId())
                            .destinationAccountNumber(accountA.getAccountNumber())
                            .amount(transferAmount)
                            .description("B to A")
                            .build();
                    transferService.executeTransfer(jwtB, key, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected failure
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // All 8 transactions must succeed without deadlock
        assertThat(successCount.get()).isEqualTo(totalThreads);

        // Net balance change is zero (equal number of transfers in each direction)
        BankAccount finalA = bankAccountRepository.findById(accountA.getId()).orElseThrow();
        BankAccount finalB = bankAccountRepository.findById(accountB.getId()).orElseThrow();
        assertThat(finalA.getBalance()).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(finalB.getBalance()).isEqualByComparingTo(new BigDecimal("1000000.00"));
    }
}
