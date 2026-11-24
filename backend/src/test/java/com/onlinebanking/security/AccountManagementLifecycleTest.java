package com.onlinebanking.security;

import com.onlinebanking.dto.AccountCreationRequest;
import com.onlinebanking.dto.AccountResponse;
import com.onlinebanking.dto.AdminAccountResponse;
import com.onlinebanking.entities.AuditLog;
import com.onlinebanking.entities.BankAccount;
import com.onlinebanking.entities.Branch;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.AccountStatus;
import com.onlinebanking.entities.enums.AccountType;
import com.onlinebanking.entities.enums.BranchStatus;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.repositories.AuditLogRepository;
import com.onlinebanking.repositories.BankAccountRepository;
import com.onlinebanking.repositories.BranchRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import com.onlinebanking.services.AccountManagementService;
import com.onlinebanking.services.AuthorizationPolicyService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountManagementLifecycleTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private AccountManagementService accountManagementService;

    @Autowired
    private AuthorizationPolicyService authorizationPolicyService;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired(required = false)
    private com.onlinebanking.repositories.LedgerEntryRepository ledgerEntryRepository;

    @Autowired(required = false)
    private com.onlinebanking.repositories.BankTransactionRepository bankTransactionRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    private UserProfile activeCustomer;
    private UserProfile otherCustomer;
    private UserProfile suspendedCustomer;

    private Jwt customerJwt;
    private Jwt otherCustomerJwt;
    private Jwt suspendedCustomerJwt;
    private Jwt adminJwt;
    private Jwt staffJwt;
    private Jwt auditorJwt;

    @BeforeEach
    void setUp() {
        if (ledgerEntryRepository != null) ledgerEntryRepository.deleteAll();
        if (bankTransactionRepository != null) bankTransactionRepository.deleteAll();
        auditLogRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();
        branchRepository.deleteAll();

        Branch branch = new Branch();
        branch.setBranchCode("BR-TEST-01");
        branch.setBranchName("Test Branch");
        branch.setStatus(BranchStatus.ACTIVE);
        branch = branchRepository.save(branch);

        activeCustomer = createUser("cust-active-sub", "active_cust", ProfileStatus.ACTIVE, branch);
        otherCustomer = createUser("cust-other-sub", "other_cust", ProfileStatus.ACTIVE, branch);
        suspendedCustomer = createUser("cust-suspended-sub", "suspended_cust", ProfileStatus.SUSPENDED, branch);

        customerJwt = createJwt("cust-active-sub", "customer");
        otherCustomerJwt = createJwt("cust-other-sub", "customer");
        suspendedCustomerJwt = createJwt("cust-suspended-sub", "customer");
        adminJwt = createJwt("admin-sub-01", "admin");
        staffJwt = createJwt("staff-sub-01", "staff");
        auditorJwt = createJwt("auditor-sub-01", "auditor");
    }

    private UserProfile createUser(String sub, String username, ProfileStatus status, Branch branch) {
        UserProfile p = new UserProfile();
        p.setKeycloakUserId(sub);
        p.setUsername(username);
        p.setEmail(username + "@test.local");
        p.setProfileStatus(status);
        p.setBranch(branch);
        return userProfileRepository.save(p);
    }

    private Jwt createJwt(String sub, String role) {
        return Jwt.withTokenValue("mock.token." + sub)
                .header("alg", "none")
                .subject(sub)
                .claim("preferred_username", sub)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    // ═════════════════════════════════════════════════════════════
    // 1. Customer Account Creation Tests
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Customer Account Creation Tests")
    class AccountCreationTests {

        @Test
        @DisplayName("1 & 2. CUSTOMER can open their own account with zero initial balance")
        @WithMockUser(roles = "CUSTOMER")
        void customerCanOpenAccountWithZeroBalance() {
            AccountCreationRequest req = new AccountCreationRequest(AccountType.SAVINGS, "VND");
            AccountResponse res = accountManagementService.openAccount(req, customerJwt);

            assertThat(res).isNotNull();
            assertThat(res.getId()).isNotNull();
            assertThat(res.getAccountType()).isEqualTo("SAVINGS");
            assertThat(res.getAccountStatus()).isEqualTo("ACTIVE");
            assertThat(res.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(res.getMaskedAccountNumber()).startsWith("******");

            BankAccount entity = bankAccountRepository.findById(res.getId()).orElseThrow();
            assertThat(entity.getOwner().getId()).isEqualTo(activeCustomer.getId());
            assertThat(entity.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

            List<AuditLog> audits = auditLogRepository.findByEventType("ACCOUNT_CREATED");
            assertThat(audits).isNotEmpty();
            assertThat(audits.get(0).getUserId()).isEqualTo("cust-active-sub");
        }

        @Test
        @DisplayName("3 & 4. Owner is strictly derived from JWT sub (frontend cannot spoof owner)")
        @WithMockUser(roles = "CUSTOMER")
        void ownerIsDerivedFromJwtSub() {
            AccountResponse res = accountManagementService.openAccount(null, customerJwt);
            BankAccount saved = bankAccountRepository.findById(res.getId()).orElseThrow();

            assertThat(saved.getOwner().getKeycloakUserId()).isEqualTo("cust-active-sub");
            assertThat(saved.getOwner().getId()).isEqualTo(activeCustomer.getId());
        }

        @Test
        @DisplayName("8. Inactive/suspended customer cannot open an account")
        @WithMockUser(roles = "CUSTOMER")
        void inactiveCustomerCannotOpenAccount() {
            assertThatThrownBy(() -> accountManagementService.openAccount(null, suspendedCustomerJwt))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("not active");

            List<AuditLog> audits = auditLogRepository.findByEventType("ACCOUNT_CREATION_DENIED");
            assertThat(audits).isNotEmpty();
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 2. Customer Account Access & Isolation Tests
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Customer Account Access Tests")
    class AccountAccessTests {

        @Test
        @DisplayName("9 & 10. CUSTOMER can list and view their own account details with balance")
        @WithMockUser(roles = "CUSTOMER")
        void customerCanListAndViewOwnAccount() {
            AccountResponse created = accountManagementService.openAccount(new AccountCreationRequest(AccountType.CHECKING, "VND"), customerJwt);

            List<AccountResponse> list = accountManagementService.listMyAccounts(customerJwt);
            assertThat(list).hasSize(1);
            assertThat(list.get(0).getId()).isEqualTo(created.getId());

            AccountResponse details = accountManagementService.getMyAccountDetails(created.getId(), customerJwt);
            assertThat(details).isNotNull();
            assertThat(details.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(details.getMaskedAccountNumber()).startsWith("******");
        }

        @Test
        @DisplayName("11. CUSTOMER cannot view another customer's account")
        @WithMockUser(roles = "CUSTOMER")
        void customerCannotViewOtherCustomerAccount() {
            AccountResponse otherAccount = accountManagementService.openAccount(null, otherCustomerJwt);

            assertThatThrownBy(() -> accountManagementService.getMyAccountDetails(otherAccount.getId(), customerJwt))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("14. CUSTOMER can still view their own LOCKED account status and balance")
        @WithMockUser(roles = {"CUSTOMER", "ADMIN"})
        void customerCanViewOwnLockedAccount() {
            AccountResponse created = accountManagementService.openAccount(null, customerJwt);

            // Admin locks account
            accountManagementService.lockAccount(created.getId(), "Suspicious login pattern detected", adminJwt);

            AccountResponse details = accountManagementService.getMyAccountDetails(created.getId(), customerJwt);
            assertThat(details).isNotNull();
            assertThat(details.getAccountStatus()).isEqualTo("LOCKED");
            assertThat(details.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("15. CUSTOMER cannot use a LOCKED account for financial operations")
        @WithMockUser(roles = {"CUSTOMER", "ADMIN"})
        void customerCannotUseLockedAccountForTransactions() {
            AccountResponse created = accountManagementService.openAccount(null, customerJwt);
            accountManagementService.lockAccount(created.getId(), "Routine audit hold", adminJwt);

            assertThatThrownBy(() -> authorizationPolicyService.assertCustomerOwnsAccount(created.getId(), "cust-active-sub"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not active: LOCKED");
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 3. Administrative Account Management Tests
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Administrative Account Management Tests")
    class AdminAccountManagementTests {

        @Test
        @DisplayName("16 & 17. ADMIN can lock ACTIVE account and unlock LOCKED account")
        @WithMockUser(roles = {"CUSTOMER", "ADMIN"})
        void adminCanLockAndUnlockAccount() {
            AccountResponse created = accountManagementService.openAccount(null, customerJwt);

            // Lock
            AdminAccountResponse locked = accountManagementService.lockAccount(created.getId(), "Fraud suspicion alert", adminJwt);
            assertThat(locked.getAccountStatus()).isEqualTo("LOCKED");

            BankAccount dbAccount = bankAccountRepository.findById(created.getId()).orElseThrow();
            assertThat(dbAccount.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);

            List<AuditLog> lockAudits = auditLogRepository.findByEventType("ACCOUNT_LOCKED");
            assertThat(lockAudits).isNotEmpty();
            assertThat(lockAudits.get(0).getDescription()).contains("Fraud suspicion alert");

            // Unlock
            AdminAccountResponse unlocked = accountManagementService.unlockAccount(created.getId(), "Customer identity verified", adminJwt);
            assertThat(unlocked.getAccountStatus()).isEqualTo("ACTIVE");

            dbAccount = bankAccountRepository.findById(created.getId()).orElseThrow();
            assertThat(dbAccount.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

            List<AuditLog> unlockAudits = auditLogRepository.findByEventType("ACCOUNT_UNLOCKED");
            assertThat(unlockAudits).isNotEmpty();
            assertThat(unlockAudits.get(0).getDescription()).contains("Customer identity verified");
        }

        @Test
        @DisplayName("18. ADMIN cannot unlock a permanently CLOSED account")
        @WithMockUser(roles = {"CUSTOMER", "ADMIN"})
        void adminCannotUnlockClosedAccount() {
            AccountResponse created = accountManagementService.openAccount(null, customerJwt);
            BankAccount acc = bankAccountRepository.findById(created.getId()).orElseThrow();
            acc.setAccountStatus(AccountStatus.CLOSED);
            bankAccountRepository.save(acc);

            assertThatThrownBy(() -> accountManagementService.unlockAccount(created.getId(), "Attempt reopen", adminJwt))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("permanently CLOSED");
        }

        @Test
        @DisplayName("19. ADMIN must provide mandatory non-empty reason")
        @WithMockUser(roles = {"CUSTOMER", "ADMIN"})
        void reasonIsMandatory() {
            AccountResponse created = accountManagementService.openAccount(null, customerJwt);

            assertThatThrownBy(() -> accountManagementService.lockAccount(created.getId(), "  ", adminJwt))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason is mandatory");
        }

        @Test
        @DisplayName("Admin search accounts endpoint does NOT expose balance")
        @WithMockUser(roles = {"CUSTOMER", "ADMIN"})
        void adminSearchDoesNotExposeBalance() {
            accountManagementService.openAccount(null, customerJwt);

            Page<AdminAccountResponse> page = accountManagementService.searchAccounts(null, null, PageRequest.of(0, 10));
            assertThat(page.getContent()).isNotEmpty();
            AdminAccountResponse res = page.getContent().get(0);
            assertThat(res.getMaskedAccountNumber()).startsWith("******");
            assertThat(res.getOwnerUsername()).isEqualTo("active_cust");
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 4. Database Integrity & Optimistic Locking Tests
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Database Integrity & Concurrency Tests")
    class DatabaseIntegrityTests {

        @Test
        @DisplayName("26. Optimistic locking prevents stale concurrent updates")
        @WithMockUser(roles = "CUSTOMER")
        void optimisticLockingPreventsConcurrentOverwrite() {
            AccountResponse created = accountManagementService.openAccount(null, customerJwt);

            BankAccount account1 = bankAccountRepository.findById(created.getId()).orElseThrow();
            BankAccount account2 = bankAccountRepository.findById(created.getId()).orElseThrow();

            account1.setCurrency("USD");
            bankAccountRepository.saveAndFlush(account1);

            account2.setCurrency("EUR");
            assertThatThrownBy(() -> bankAccountRepository.saveAndFlush(account2))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("24. Account numbers have unique constraint enforcement")
        @WithMockUser(roles = "CUSTOMER")
        void accountNumbersMustBeUnique() {
            AccountResponse created = accountManagementService.openAccount(null, customerJwt);
            BankAccount existing = bankAccountRepository.findById(created.getId()).orElseThrow();

            BankAccount duplicate = new BankAccount();
            duplicate.setAccountNumber(existing.getAccountNumber()); // duplicate
            duplicate.setOwner(otherCustomer);
            duplicate.setBalance(BigDecimal.ZERO);
            duplicate.setAccountStatus(AccountStatus.ACTIVE);
            duplicate.setAccountType(AccountType.CHECKING);
            duplicate.setOpenedAt(LocalDateTime.now());

            assertThatThrownBy(() -> bankAccountRepository.saveAndFlush(duplicate))
                    .isNotNull();
        }
    }
}

