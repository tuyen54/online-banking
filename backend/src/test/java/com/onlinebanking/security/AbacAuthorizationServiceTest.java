package com.onlinebanking.security;

import com.onlinebanking.dto.BankAccountResponse;
import com.onlinebanking.dto.CustomerProfileResponse;
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
import com.onlinebanking.services.AccountAccessService;
import com.onlinebanking.services.AuthorizationPolicyService;
import com.onlinebanking.services.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AbacAuthorizationServiceTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private AuthorizationPolicyService authorizationPolicyService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountAccessService accountAccessService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired(required = false)
    private com.onlinebanking.repositories.LedgerEntryRepository ledgerEntryRepository;

    @Autowired(required = false)
    private com.onlinebanking.repositories.BankTransactionRepository bankTransactionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Branch branchA;
    private Branch branchB;
    private Branch inactiveBranch;

    private UserProfile customerA;
    private UserProfile customerB;
    private UserProfile customerNoBranch;
    private UserProfile inactiveCustomer;

    private UserProfile staffA;
    private UserProfile staffNoBranch;
    private UserProfile staffInactiveBranch;

    private BankAccount accountA1;
    private BankAccount accountA2;
    private BankAccount accountB1;
    private BankAccount frozenAccountA;

    @BeforeEach
    void setUp() {
        if (ledgerEntryRepository != null) ledgerEntryRepository.deleteAll();
        if (bankTransactionRepository != null) bankTransactionRepository.deleteAll();
        auditLogRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();
        branchRepository.deleteAll();

        // Branches
        branchA = createBranch("BR-HCM-01", "HCM Branch", BranchStatus.ACTIVE);
        branchB = createBranch("BR-HN-01", "Hanoi Branch", BranchStatus.ACTIVE);
        inactiveBranch = createBranch("BR-OLD-01", "Closed Branch", BranchStatus.INACTIVE);

        // Customers
        customerA = createUserProfile("keycloak-cust-a", "cust_a", "cust_a@bank.local", ProfileStatus.ACTIVE, branchA);
        customerB = createUserProfile("keycloak-cust-b", "cust_b", "cust_b@bank.local", ProfileStatus.ACTIVE, branchB);
        customerNoBranch = createUserProfile("keycloak-cust-nobranch", "cust_nobranch", "nobranch@bank.local", ProfileStatus.ACTIVE, null);
        inactiveCustomer = createUserProfile("keycloak-cust-inactive", "cust_inactive", "inactive@bank.local", ProfileStatus.SUSPENDED, branchA);

        // Staff
        staffA = createUserProfile("keycloak-staff-a", "staff_a", "staff_a@bank.local", ProfileStatus.ACTIVE, branchA);
        staffNoBranch = createUserProfile("keycloak-staff-nobranch", "staff_nobranch", "staffnobranch@bank.local", ProfileStatus.ACTIVE, null);
        staffInactiveBranch = createUserProfile("keycloak-staff-inactbr", "staff_inactbr", "staffinactbr@bank.local", ProfileStatus.ACTIVE, inactiveBranch);

        // Bank Accounts
        accountA1 = createBankAccount("100011112222", customerA, AccountType.CHECKING, AccountStatus.ACTIVE, "VND");
        accountA2 = createBankAccount("100033334444", customerA, AccountType.SAVINGS, AccountStatus.ACTIVE, "USD");
        accountB1 = createBankAccount("200055556666", customerB, AccountType.CHECKING, AccountStatus.ACTIVE, "VND");
        frozenAccountA = createBankAccount("100099998888", customerA, AccountType.CHECKING, AccountStatus.FROZEN, "VND");
    }

    private Branch createBranch(String code, String name, BranchStatus status) {
        Branch b = new Branch();
        b.setBranchCode(code);
        b.setBranchName(name);
        b.setStatus(status);
        return branchRepository.save(b);
    }

    private UserProfile createUserProfile(String keycloakId, String username, String email, ProfileStatus status, Branch branch) {
        UserProfile p = new UserProfile();
        p.setKeycloakUserId(keycloakId);
        p.setUsername(username);
        p.setEmail(email);
        p.setDisplayName(username);
        p.setProfileStatus(status);
        p.setBranch(branch);
        return userProfileRepository.save(p);
    }

    private BankAccount createBankAccount(String num, UserProfile owner, AccountType type, AccountStatus status, String cur) {
        BankAccount a = new BankAccount();
        a.setAccountNumber(num);
        a.setOwner(owner);
        a.setAccountType(type);
        a.setAccountStatus(status);
        a.setCurrency(cur);
        a.setOpenedAt(LocalDateTime.now());
        return bankAccountRepository.save(a);
    }

    private Jwt createJwt(String sub, String role) {
        return Jwt.withTokenValue("mock.jwt." + sub)
                .header("alg", "none")
                .subject(sub)
                .claim("preferred_username", sub)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    // ═════════════════════════════════════════════════════════════
    // 1. Customer Ownership Tests
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Customer Ownership Tests")
    class CustomerOwnershipTests {

        @Test
        @DisplayName("1. CUSTOMER A can access their own profile")
        @WithMockUser(roles = "CUSTOMER")
        void customerCanAccessOwnProfile() {
            Jwt jwt = createJwt("keycloak-cust-a", "customer");
            CustomerProfileResponse res = customerService.getCustomerProfile(customerA.getId(), jwt);

            assertThat(res).isNotNull();
            assertThat(res.getId()).isEqualTo(customerA.getId());
            assertThat(res.getUsername()).isEqualTo("cust_a");
        }

        @Test
        @DisplayName("2. CUSTOMER A cannot access CUSTOMER B's profile (ABAC denial)")
        @WithMockUser(roles = "CUSTOMER")
        void customerCannotAccessOtherCustomerProfile() {
            Jwt jwt = createJwt("keycloak-cust-a", "customer");

            assertThatThrownBy(() -> customerService.getCustomerProfile(customerB.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("You can only access your own profile");

            List<AuditLog> audits = auditLogRepository.findByEventType("ABAC_ACCESS_DENIED");
            assertThat(audits).isNotEmpty();
            assertThat(audits.get(0).getUserId()).isEqualTo("keycloak-cust-a");
        }

        @Test
        @DisplayName("3. CUSTOMER A can list only their own accounts")
        @WithMockUser(roles = "CUSTOMER")
        void customerCanListOnlyOwnAccounts() {
            Jwt jwt = createJwt("keycloak-cust-a", "customer");
            List<BankAccountResponse> accounts = accountAccessService.listMyAccounts(jwt);

            assertThat(accounts).hasSize(3); // accountA1, accountA2, frozenAccountA
            assertThat(accounts).extracting(BankAccountResponse::getMaskedAccountNumber)
                    .containsExactlyInAnyOrder("******2222", "******4444", "******8888");
        }

        @Test
        @DisplayName("4. CUSTOMER A can access their own account details with masked number")
        @WithMockUser(roles = "CUSTOMER")
        void customerCanAccessOwnAccount() {
            Jwt jwt = createJwt("keycloak-cust-a", "customer");
            BankAccountResponse res = accountAccessService.getAccountDetails(accountA1.getId(), jwt);

            assertThat(res).isNotNull();
            assertThat(res.getId()).isEqualTo(accountA1.getId());
            assertThat(res.getMaskedAccountNumber()).isEqualTo("******2222");
        }

        @Test
        @DisplayName("5. CUSTOMER A cannot access CUSTOMER B's account (BOLA protection)")
        @WithMockUser(roles = "CUSTOMER")
        void customerCannotAccessOtherCustomerAccount() {
            Jwt jwt = createJwt("keycloak-cust-a", "customer");

            assertThatThrownBy(() -> accountAccessService.getAccountDetails(accountB1.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class);

            List<AuditLog> audits = auditLogRepository.findByEventType("ABAC_ACCESS_DENIED");
            assertThat(audits).isNotEmpty();
        }

        @Test
        @DisplayName("6. Changing accountId cannot bypass ownership validation")
        @WithMockUser(roles = "CUSTOMER")
        void arbitraryAccountId_cannotBypassOwnership() {
            Jwt jwt = createJwt("keycloak-cust-a", "customer");
            UUID randomId = UUID.randomUUID();

            assertThatThrownBy(() -> accountAccessService.getAccountDetails(randomId, jwt))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("7. Customer with inactive profile is denied")
        @WithMockUser(roles = "CUSTOMER")
        void inactiveCustomerIsDenied() {
            Jwt jwt = createJwt("keycloak-cust-inactive", "customer");

            assertThatThrownBy(() -> customerService.getCustomerProfile(inactiveCustomer.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("not active");
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 2. Staff Branch Isolation Tests
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Staff Branch Isolation Tests")
    class StaffBranchIsolationTests {

        @Test
        @DisplayName("9. STAFF A can access customers in Branch A")
        @WithMockUser(roles = "STAFF")
        void staffCanAccessCustomerInSameBranch() {
            Jwt jwt = createJwt("keycloak-staff-a", "staff");
            CustomerProfileResponse res = customerService.getCustomerProfile(customerA.getId(), jwt);

            assertThat(res).isNotNull();
            assertThat(res.getId()).isEqualTo(customerA.getId());
            assertThat(res.getBranch().getBranchCode()).isEqualTo("BR-HCM-01");
        }

        @Test
        @DisplayName("10. STAFF A cannot access customers in Branch B (cross-branch denied)")
        @WithMockUser(roles = "STAFF")
        void staffCannotAccessCustomerInDifferentBranch() {
            Jwt jwt = createJwt("keycloak-staff-a", "staff");

            assertThatThrownBy(() -> customerService.getCustomerProfile(customerB.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("assigned branch");

            List<AuditLog> audits = auditLogRepository.findByEventType("ABAC_ACCESS_DENIED");
            assertThat(audits).isNotEmpty();
            assertThat(audits.get(0).getDescription()).contains("Staff branch [BR-HCM-01] does not match customer branch [BR-HN-01]");
        }

        @Test
        @DisplayName("11. STAFF with no assigned branch is denied")
        @WithMockUser(roles = "STAFF")
        void staffWithoutBranchIsDenied() {
            Jwt jwt = createJwt("keycloak-staff-nobranch", "staff");

            assertThatThrownBy(() -> customerService.getCustomerProfile(customerA.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("no assigned branch");
        }

        @Test
        @DisplayName("12. STAFF with inactive branch is denied")
        @WithMockUser(roles = "STAFF")
        void staffWithInactiveBranchIsDenied() {
            Jwt jwt = createJwt("keycloak-staff-inactbr", "staff");

            assertThatThrownBy(() -> customerService.getCustomerProfile(customerA.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("branch is inactive");
        }

        @Test
        @DisplayName("13. STAFF cannot access customers without a branch assignment")
        @WithMockUser(roles = "STAFF")
        void staffCannotAccessCustomerWithoutBranch() {
            Jwt jwt = createJwt("keycloak-staff-a", "staff");

            assertThatThrownBy(() -> customerService.getCustomerProfile(customerNoBranch.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Customer has no assigned branch");
        }

        @Test
        @DisplayName("15. STAFF cannot access account balances through customer account endpoints")
        @WithMockUser(roles = "STAFF")
        void staffCannotAccessCustomerAccounts() {
            Jwt jwt = createJwt("keycloak-staff-a", "staff");

            assertThatThrownBy(() -> accountAccessService.getAccountDetails(accountA1.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 3. Admin and Auditor Access Tests
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Admin and Auditor Access Policy Tests")
    class AdminAndAuditorTests {

        @Test
        @DisplayName("17. ADMIN cannot bypass explicit customer-access policies")
        @WithMockUser(roles = "ADMIN")
        void adminCannotAccessCustomerProfileDirectly() {
            Jwt jwt = createJwt("admin01", "admin");

            assertThatThrownBy(() -> customerService.getCustomerProfile(customerA.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("ADMIN cannot access customer accounts")
        @WithMockUser(roles = "ADMIN")
        void adminCannotAccessCustomerAccount() {
            Jwt jwt = createJwt("admin01", "admin");

            assertThatThrownBy(() -> accountAccessService.getAccountDetails(accountA1.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("18. AUDITOR cannot access customer profile directly")
        @WithMockUser(roles = "AUDITOR")
        void auditorCannotAccessCustomerProfile() {
            Jwt jwt = createJwt("auditor01", "auditor");

            assertThatThrownBy(() -> customerService.getCustomerProfile(customerA.getId(), jwt))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 4. Source Account Ownership Assertion (for future transfers)
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Source Account Ownership Validation Tests")
    class SourceAccountOwnershipTests {

        @Test
        @DisplayName("Valid customer owning active account succeeds")
        void validCustomerAndActiveAccount_succeeds() {
            BankAccount result = authorizationPolicyService.assertCustomerOwnsAccount(
                    accountA1.getId(), "keycloak-cust-a");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(accountA1.getId());
        }

        @Test
        @DisplayName("Customer attempting to use another's account fails")
        void differentCustomer_fails() {
            assertThatThrownBy(() -> authorizationPolicyService.assertCustomerOwnsAccount(
                    accountB1.getId(), "keycloak-cust-a"))
                    .isInstanceOf(AccessDeniedException.class);

            List<AuditLog> audits = auditLogRepository.findByEventType("ABAC_ACCESS_DENIED");
            assertThat(audits).isNotEmpty();
        }

        @Test
        @DisplayName("Attempting to use frozen or closed account fails")
        void frozenAccount_fails() {
            assertThatThrownBy(() -> authorizationPolicyService.assertCustomerOwnsAccount(
                    frozenAccountA.getId(), "keycloak-cust-a"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not active");
        }

        @Test
        @DisplayName("Inactive customer profile cannot use accounts")
        void inactiveCustomerProfile_fails() {
            BankAccount inactiveAcc = createBankAccount("999900001111", inactiveCustomer, AccountType.CHECKING, AccountStatus.ACTIVE, "VND");

            assertThatThrownBy(() -> authorizationPolicyService.assertCustomerOwnsAccount(
                    inactiveAcc.getId(), "keycloak-cust-inactive"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("inactive");
        }
    }
}
