package com.onlinebanking.security;

import com.onlinebanking.dto.TransferRequest;
import com.onlinebanking.dto.TransferResponse;
import com.onlinebanking.entities.AuditLog;
import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.entities.BankTransaction;
import com.onlinebanking.entities.Branch;
import com.onlinebanking.entities.LedgerEntry;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.AccountType;
import com.onlinebanking.entities.enums.BranchStatus;
import com.onlinebanking.entities.enums.LedgerEntryType;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.entities.enums.TransactionStatus;
import com.onlinebanking.exceptions.IdempotencyConflictException;
import com.onlinebanking.repositories.AuditLogRepository;
import com.onlinebanking.repositories.BankAccountRepository;
import com.onlinebanking.repositories.BankTransactionRepository;
import com.onlinebanking.repositories.BranchRepository;
import com.onlinebanking.repositories.LedgerEntryRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import com.onlinebanking.services.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransferServiceTest {

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

    private Branch testBranch;
    private UserProfile senderProfile;
    private UserProfile recipientProfile;
    private BankAccount senderAccount;
    private BankAccount recipientAccount;

    private Jwt senderJwt;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        bankTransactionRepository.deleteAll();
        auditLogRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();
        branchRepository.deleteAll();

        testBranch = new Branch();
        testBranch.setBranchCode("BR-TEST");
        testBranch.setBranchName("Test Branch");
        testBranch.setStatus(BranchStatus.ACTIVE);
        testBranch = branchRepository.save(testBranch);

        senderProfile = new UserProfile();
        senderProfile.setKeycloakUserId("sender-keycloak-sub");
        senderProfile.setUsername("sender_user");
        senderProfile.setEmail("sender@test.com");
        senderProfile.setFirstName("Sender");
        senderProfile.setLastName("User");
        senderProfile.setProfileStatus(ProfileStatus.ACTIVE);
        senderProfile.setBranch(testBranch);
        senderProfile = userProfileRepository.save(senderProfile);

        recipientProfile = new UserProfile();
        recipientProfile.setKeycloakUserId("recipient-keycloak-sub");
        recipientProfile.setUsername("recipient_user");
        recipientProfile.setEmail("recipient@test.com");
        recipientProfile.setFirstName("Recipient");
        recipientProfile.setLastName("User");
        recipientProfile.setProfileStatus(ProfileStatus.ACTIVE);
        recipientProfile.setBranch(testBranch);
        recipientProfile = userProfileRepository.save(recipientProfile);

        senderAccount = new BankAccount();
        senderAccount.setAccountNumber("100111111111");
        senderAccount.setAccountType(AccountType.CHECKING);
        senderAccount.setAccountStatus(AccountStatus.ACTIVE);
        senderAccount.setBalance(new BigDecimal("5000000.00"));
        senderAccount.setCurrency("VND");
        senderAccount.setOwner(senderProfile);
        senderAccount = bankAccountRepository.save(senderAccount);

        recipientAccount = new BankAccount();
        recipientAccount.setAccountNumber("100222222222");
        recipientAccount.setAccountType(AccountType.CHECKING);
        recipientAccount.setAccountStatus(AccountStatus.ACTIVE);
        recipientAccount.setBalance(new BigDecimal("1000000.00"));
        recipientAccount.setCurrency("VND");
        recipientAccount.setOwner(recipientProfile);
        recipientAccount = bankAccountRepository.save(recipientAccount);

        senderJwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "sender-keycloak-sub",
                        "preferred_username", "sender_user",
                        "realm_access", Map.of("roles", List.of("customer"))
                )
        );
    }

    @Nested
    @DisplayName("Successful Transfer Execution & Ledger Invariants")
    class SuccessfulTransferTests {

        @Test
        @DisplayName("Valid transfer debits sender, credits recipient, and creates balanced ledger entries")
        void executeValidTransfer() {
            BigDecimal transferAmount = new BigDecimal("1500000.00");
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(recipientAccount.getAccountNumber())
                    .amount(transferAmount)
                    .description("Tuition payment")
                    .build();

            String idempotencyKey = "key-" + UUID.randomUUID();
            TransferResponse response = transferService.executeTransfer(senderJwt, idempotencyKey, request);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.getAmount()).isEqualByComparingTo(transferAmount);
            assertThat(response.getTransactionReference()).startsWith("TRF-");

            // Verify account balances
            BankAccount updatedSender = bankAccountRepository.findById(senderAccount.getId()).orElseThrow();
            BankAccount updatedRecipient = bankAccountRepository.findById(recipientAccount.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("3500000.00"));
            assertThat(updatedRecipient.getBalance()).isEqualByComparingTo(new BigDecimal("2500000.00"));

            // Verify BankTransaction
            BankTransaction tx = bankTransactionRepository.findByTransactionReference(response.getTransactionReference()).orElseThrow();
            assertThat(tx.getAmount()).isEqualByComparingTo(transferAmount);
            assertThat(tx.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(tx.getIdempotencyKey()).isEqualTo(idempotencyKey);

            // Verify Double-Entry Ledger
            List<LedgerEntry> entries = ledgerEntryRepository.findByBankTransactionId(tx.getId());
            assertThat(entries).hasSize(2);

            LedgerEntry debitEntry = entries.stream()
                    .filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                    .findFirst().orElseThrow();
            LedgerEntry creditEntry = entries.stream()
                    .filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                    .findFirst().orElseThrow();

            assertThat(debitEntry.getBankAccount().getId()).isEqualTo(senderAccount.getId());
            assertThat(debitEntry.getAmount()).isEqualByComparingTo(transferAmount);

            assertThat(creditEntry.getBankAccount().getId()).isEqualTo(recipientAccount.getId());
            assertThat(creditEntry.getAmount()).isEqualByComparingTo(transferAmount);

            // Invariant: SUM(DEBIT) == SUM(CREDIT)
            assertThat(debitEntry.getAmount()).isEqualByComparingTo(creditEntry.getAmount());

            // Verify Audit Log
            List<AuditLog> auditLogs = auditLogRepository.findByEventType("TRANSFER_COMPLETED");
            assertThat(auditLogs).isNotEmpty();
            assertThat(auditLogs.get(0).getUserId()).isEqualTo("sender-keycloak-sub");
        }
    }

    @Nested
    @DisplayName("Idempotency Replay and Conflict Handling")
    class IdempotencyTests {

        @Test
        @DisplayName("Replaying same transfer with identical idempotency key returns cached response without duplicate debit")
        void idempotentReplayReturnsCachedResponse() {
            BigDecimal transferAmount = new BigDecimal("500000.00");
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(recipientAccount.getAccountNumber())
                    .amount(transferAmount)
                    .description("Dinner bill")
                    .build();

            String idempotencyKey = "idemp-key-repeat-1";

            TransferResponse resp1 = transferService.executeTransfer(senderJwt, idempotencyKey, request);
            TransferResponse resp2 = transferService.executeTransfer(senderJwt, idempotencyKey, request);

            assertThat(resp1.getTransactionReference()).isEqualTo(resp2.getTransactionReference());

            // Verify sender was debited exactly once
            BankAccount updatedSender = bankAccountRepository.findById(senderAccount.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("4500000.00"));

            // Verify only 1 transaction recorded
            List<BankTransaction> transactions = bankTransactionRepository.findAll();
            assertThat(transactions).hasSize(1);
        }

        @Test
        @DisplayName("Reusing idempotency key with different payload throws IdempotencyConflictException (409)")
        void conflictingPayloadThrowsIdempotencyConflict() {
            String idempotencyKey = "idemp-conflict-key";

            TransferRequest req1 = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(recipientAccount.getAccountNumber())
                    .amount(new BigDecimal("500000.00"))
                    .description("First payment")
                    .build();

            transferService.executeTransfer(senderJwt, idempotencyKey, req1);

            TransferRequest req2 = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(recipientAccount.getAccountNumber())
                    .amount(new BigDecimal("700000.00")) // Different amount!
                    .description("First payment")
                    .build();

            assertThatThrownBy(() -> transferService.executeTransfer(senderJwt, idempotencyKey, req2))
                    .isInstanceOf(IdempotencyConflictException.class)
                    .hasMessageContaining("different request payload");
        }

        @Test
        @DisplayName("Missing idempotency key throws IllegalArgumentException")
        void missingIdempotencyKeyThrowsException() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(recipientAccount.getAccountNumber())
                    .amount(new BigDecimal("100000.00"))
                    .build();

            assertThatThrownBy(() -> transferService.executeTransfer(senderJwt, null, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency-Key");

            assertThatThrownBy(() -> transferService.executeTransfer(senderJwt, "   ", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency-Key");
        }
    }

    @Nested
    @DisplayName("Validation & Business Rule Failures")
    class ValidationFailureTests {

        @Test
        @DisplayName("Transfer with insufficient funds fails and preserves balances")
        void insufficientFundsThrowsException() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(recipientAccount.getAccountNumber())
                    .amount(new BigDecimal("6000000.00")) // Exceeds balance of 5,000,000
                    .description("Overdraft attempt")
                    .build();

            assertThatThrownBy(() -> transferService.executeTransfer(senderJwt, "insufficient-funds-key", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Insufficient balance");

            // Verify balances untouched
            BankAccount updatedSender = bankAccountRepository.findById(senderAccount.getId()).orElseThrow();
            assertThat(updatedSender.getBalance()).isEqualByComparingTo(new BigDecimal("5000000.00"));
        }

        @Test
        @DisplayName("Transfer to same account fails")
        void transferToSelfFails() {
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(senderAccount.getAccountNumber())
                    .amount(new BigDecimal("100000.00"))
                    .build();

            assertThatThrownBy(() -> transferService.executeTransfer(senderJwt, "same-account-key", request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot transfer funds to the same account");
        }

        @Test
        @DisplayName("Transfer from non-owned account fails ABAC ownership check")
        void transferFromNonOwnedAccountFailsAbac() {
            // Sender attempts to debit recipient's account
            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(recipientAccount.getId())
                    .destinationAccountNumber(senderAccount.getAccountNumber())
                    .amount(new BigDecimal("100000.00"))
                    .build();

            assertThatThrownBy(() -> transferService.executeTransfer(senderJwt, "abac-violation-key", request))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Transfer from frozen or closed account fails")
        void transferFromInactiveAccountFails() {
            senderAccount.setAccountStatus(AccountStatus.FROZEN);
            bankAccountRepository.save(senderAccount);

            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(recipientAccount.getAccountNumber())
                    .amount(new BigDecimal("100000.00"))
                    .build();

            assertThatThrownBy(() -> transferService.executeTransfer(senderJwt, "frozen-account-key", request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Account is not active");
        }

        @Test
        @DisplayName("Transfer to inactive destination account fails")
        void transferToInactiveDestinationFails() {
            recipientAccount.setAccountStatus(AccountStatus.FROZEN);
            bankAccountRepository.save(recipientAccount);

            TransferRequest request = TransferRequest.builder()
                    .sourceAccountId(senderAccount.getId())
                    .destinationAccountNumber(recipientAccount.getAccountNumber())
                    .amount(new BigDecimal("100000.00"))
                    .build();

            assertThatThrownBy(() -> transferService.executeTransfer(senderJwt, "frozen-dest-key", request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Destination account is not active");
        }
    }
}
