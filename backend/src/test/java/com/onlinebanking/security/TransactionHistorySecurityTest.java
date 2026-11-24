package com.onlinebanking.security;

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
import com.onlinebanking.repositories.AuditLogRepository;
import com.onlinebanking.repositories.BankAccountRepository;
import com.onlinebanking.repositories.BankTransactionRepository;
import com.onlinebanking.repositories.BranchRepository;
import com.onlinebanking.repositories.LedgerEntryRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionHistorySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private BankTransactionRepository bankTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Branch branchHanoi;
    private Branch branchSaigon;

    private UserProfile customerHanoi;
    private UserProfile customerSaigon;
    private UserProfile staffHanoi;

    private BankAccount accountHanoi;
    private BankAccount accountSaigon;

    private BankTransaction txHanoiToSaigon;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        bankTransactionRepository.deleteAll();
        auditLogRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userProfileRepository.deleteAll();
        branchRepository.deleteAll();

        branchHanoi = new Branch();
        branchHanoi.setBranchCode("BR-HN");
        branchHanoi.setBranchName("Hanoi Branch");
        branchHanoi.setStatus(BranchStatus.ACTIVE);
        branchHanoi = branchRepository.save(branchHanoi);

        branchSaigon = new Branch();
        branchSaigon.setBranchCode("BR-SG");
        branchSaigon.setBranchName("Saigon Branch");
        branchSaigon.setStatus(BranchStatus.ACTIVE);
        branchSaigon = branchRepository.save(branchSaigon);

        customerHanoi = new UserProfile();
        customerHanoi.setKeycloakUserId("cust-hn-sub");
        customerHanoi.setUsername("cust_hn");
        customerHanoi.setEmail("hn@test.com");
        customerHanoi.setFirstName("Hanoi");
        customerHanoi.setLastName("Customer");
        customerHanoi.setProfileStatus(ProfileStatus.ACTIVE);
        customerHanoi.setBranch(branchHanoi);
        customerHanoi = userProfileRepository.save(customerHanoi);

        customerSaigon = new UserProfile();
        customerSaigon.setKeycloakUserId("cust-sg-sub");
        customerSaigon.setUsername("cust_sg");
        customerSaigon.setEmail("sg@test.com");
        customerSaigon.setFirstName("Saigon");
        customerSaigon.setLastName("Customer");
        customerSaigon.setProfileStatus(ProfileStatus.ACTIVE);
        customerSaigon.setBranch(branchSaigon);
        customerSaigon = userProfileRepository.save(customerSaigon);

        staffHanoi = new UserProfile();
        staffHanoi.setKeycloakUserId("staff-hn-sub");
        staffHanoi.setUsername("staff_hn");
        staffHanoi.setEmail("staffhn@test.com");
        staffHanoi.setFirstName("Hanoi");
        staffHanoi.setLastName("Staff");
        staffHanoi.setProfileStatus(ProfileStatus.ACTIVE);
        staffHanoi.setBranch(branchHanoi);
        staffHanoi = userProfileRepository.save(staffHanoi);

        accountHanoi = new BankAccount();
        accountHanoi.setAccountNumber("100111111001");
        accountHanoi.setAccountType(AccountType.CHECKING);
        accountHanoi.setAccountStatus(AccountStatus.ACTIVE);
        accountHanoi.setBalance(new BigDecimal("5000000.00"));
        accountHanoi.setCurrency("VND");
        accountHanoi.setOwner(customerHanoi);
        accountHanoi = bankAccountRepository.save(accountHanoi);

        accountSaigon = new BankAccount();
        accountSaigon.setAccountNumber("100222222002");
        accountSaigon.setAccountType(AccountType.SAVINGS);
        accountSaigon.setAccountStatus(AccountStatus.ACTIVE);
        accountSaigon.setBalance(new BigDecimal("3000000.00"));
        accountSaigon.setCurrency("VND");
        accountSaigon.setOwner(customerSaigon);
        accountSaigon = bankAccountRepository.save(accountSaigon);

        txHanoiToSaigon = new BankTransaction();
        txHanoiToSaigon.setTransactionReference("TRF-HN-SG-001");
        txHanoiToSaigon.setInitiator(customerHanoi);
        txHanoiToSaigon.setSourceAccount(accountHanoi);
        txHanoiToSaigon.setDestinationAccount(accountSaigon);
        txHanoiToSaigon.setAmount(new BigDecimal("1000000.00"));
        txHanoiToSaigon.setCurrency("VND");
        txHanoiToSaigon.setDescription("Transfer from Hanoi to Saigon");
        txHanoiToSaigon.setStatus(TransactionStatus.COMPLETED);
        txHanoiToSaigon.setIdempotencyKey("idemp-setup-001");
        txHanoiToSaigon.setRequestHash("hash001");
        txHanoiToSaigon = bankTransactionRepository.save(txHanoiToSaigon);

        LedgerEntry debit = new LedgerEntry();
        debit.setBankTransaction(txHanoiToSaigon);
        debit.setBankAccount(accountHanoi);
        debit.setEntryType(LedgerEntryType.DEBIT);
        debit.setAmount(new BigDecimal("1000000.00"));
        debit.setCurrency("VND");
        ledgerEntryRepository.save(debit);

        LedgerEntry credit = new LedgerEntry();
        credit.setBankTransaction(txHanoiToSaigon);
        credit.setBankAccount(accountSaigon);
        credit.setEntryType(LedgerEntryType.CREDIT);
        credit.setAmount(new BigDecimal("1000000.00"));
        credit.setCurrency("VND");
        ledgerEntryRepository.save(credit);
    }

    @Nested
    @DisplayName("CUSTOMER Role Scoping")
    class CustomerHistoryTests {

        @Test
        @DisplayName("Sender customer can view their outgoing transaction with direction DEBIT and masked account numbers")
        void senderCanViewTransaction() throws Exception {
            mockMvc.perform(get("/api/transactions")
                            .with(jwt().jwt(builder -> builder.subject("cust-hn-sub"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                    .andExpect(jsonPath("$.content[0].sourceAccountNumber").value("******1001"))
                    .andExpect(jsonPath("$.content[0].destinationAccountNumber").value("******2002"));
        }

        @Test
        @DisplayName("Recipient customer can view incoming transaction with direction CREDIT")
        void recipientCanViewTransaction() throws Exception {
            mockMvc.perform(get("/api/transactions")
                            .with(jwt().jwt(builder -> builder.subject("cust-sg-sub"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].direction").value("CREDIT"));
        }

        @Test
        @DisplayName("Unrelated customer sees empty history and is denied direct access to other's transaction")
        void unrelatedCustomerCannotAccessTransaction() throws Exception {
            UserProfile stranger = new UserProfile();
            stranger.setKeycloakUserId("stranger-sub");
            stranger.setUsername("stranger");
            stranger.setEmail("stranger@test.com");
            stranger.setFirstName("Other");
            stranger.setLastName("Person");
            stranger.setProfileStatus(ProfileStatus.ACTIVE);
            userProfileRepository.save(stranger);

            mockMvc.perform(get("/api/transactions")
                            .with(jwt().jwt(builder -> builder.subject("stranger-sub"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));

            mockMvc.perform(get("/api/transactions/" + txHanoiToSaigon.getId())
                            .with(jwt().jwt(builder -> builder.subject("stranger-sub"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("STAFF Role Branch Scoping")
    class StaffHistoryTests {

        @Test
        @DisplayName("Staff member can view transactions involving accounts from their assigned branch")
        void staffCanViewBranchTransactions() throws Exception {
            mockMvc.perform(get("/api/transactions")
                            .with(jwt().jwt(builder -> builder.subject("staff-hn-sub"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].transactionReference").value("TRF-HN-SG-001"));
        }

        @Test
        @DisplayName("Staff cannot access transaction entirely outside their branch")
        void staffCannotAccessForeignBranchTransaction() throws Exception {
            // Create a transaction purely within Saigon branch
            BankAccount accountSaigon2 = new BankAccount();
            accountSaigon2.setAccountNumber("100222222003");
            accountSaigon2.setAccountType(AccountType.SAVINGS);
            accountSaigon2.setAccountStatus(AccountStatus.ACTIVE);
            accountSaigon2.setBalance(new BigDecimal("1000000.00"));
            accountSaigon2.setCurrency("VND");
            accountSaigon2.setOwner(customerSaigon);
            accountSaigon2 = bankAccountRepository.save(accountSaigon2);

            BankTransaction saigonInternalTx = new BankTransaction();
            saigonInternalTx.setTransactionReference("TRF-SG-ONLY");
            saigonInternalTx.setInitiator(customerSaigon);
            saigonInternalTx.setSourceAccount(accountSaigon);
            saigonInternalTx.setDestinationAccount(accountSaigon2);
            saigonInternalTx.setAmount(new BigDecimal("50000.00"));
            saigonInternalTx.setCurrency("VND");
            saigonInternalTx.setStatus(TransactionStatus.COMPLETED);
            saigonInternalTx.setIdempotencyKey("idemp-sg-only");
            saigonInternalTx.setRequestHash("hash-sg");
            saigonInternalTx = bankTransactionRepository.save(saigonInternalTx);

            mockMvc.perform(get("/api/transactions/" + saigonInternalTx.getId())
                            .with(jwt().jwt(builder -> builder.subject("staff-hn-sub"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("AUDITOR and ADMIN Access Control")
    class AuditorAndAdminTests {

        @Test
        @DisplayName("AUDITOR can view all transactions chronologically")
        void auditorCanViewAllTransactions() throws Exception {
            mockMvc.perform(get("/api/transactions")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));

            mockMvc.perform(get("/api/transactions/" + txHanoiToSaigon.getId())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactionReference").value("TRF-HN-SG-001"));
        }

        @Test
        @DisplayName("ADMIN is strictly DENIED access to transaction history endpoints (403)")
        void adminIsDeniedTransactionAccess() throws Exception {
            mockMvc.perform(get("/api/transactions")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/transactions/" + txHanoiToSaigon.getId())
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());
        }
    }
}
