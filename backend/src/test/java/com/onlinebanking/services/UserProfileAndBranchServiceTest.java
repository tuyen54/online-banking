package com.onlinebanking.services;

import com.onlinebanking.dto.BranchResponse;
import com.onlinebanking.dto.UserProfileResponse;
import com.onlinebanking.entities.Branch;
import com.onlinebanking.entities.IdentityGroup;
import com.onlinebanking.entities.UserGroupMembership;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.BranchStatus;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.repositories.AuditLogRepository;
import com.onlinebanking.repositories.BranchRepository;
import com.onlinebanking.repositories.IdentityGroupRepository;
import com.onlinebanking.repositories.UserGroupMembershipRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserProfileAndBranchServiceTest {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private IdentityGroupRepository identityGroupRepository;

    @Autowired
    private UserGroupMembershipRepository userGroupMembershipRepository;

    @Autowired
    private com.onlinebanking.repositories.BankAccountRepository bankAccountRepository;

    @Autowired
    private com.onlinebanking.repositories.BankTransactionRepository bankTransactionRepository;

    @Autowired
    private com.onlinebanking.repositories.LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanUp() {
        ledgerEntryRepository.deleteAll();
        bankTransactionRepository.deleteAll();
        bankAccountRepository.deleteAll();
        userGroupMembershipRepository.deleteAll();
        userProfileRepository.deleteAll();
        branchRepository.deleteAll();
        identityGroupRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    private JwtAuthenticationToken createJwtToken(String sub, String username, String email, List<String> groups, List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("mock.token.val")
                .header("alg", "RS256")
                .subject(sub)
                .claim("preferred_username", username)
                .claim("email", email)
                .claim("given_name", "First")
                .claim("family_name", "Last")
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));

        if (groups != null) {
            builder.claim("groups", groups);
        }

        Jwt jwt = builder.build();
        List<SimpleGrantedAuthority> authorities = roles == null ? List.of() : roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();

        return new JwtAuthenticationToken(jwt, authorities);
    }

    @Nested
    @DisplayName("User Identity Mapping & Provisioning Tests")
    class UserIdentityMappingTests {

        @Test
        @DisplayName("Valid JWT with unknown sub creates a new UserProfile")
        void unknownSub_shouldCreateUserProfile() {
            JwtAuthenticationToken token = createJwtToken("keycloak-sub-100", "testuser", "test@banking.local", null, List.of("CUSTOMER"));

            UserProfileResponse res = userProfileService.getOrCreateCurrentUser(token);

            assertThat(res).isNotNull();
            assertThat(res.getKeycloakUserId()).isEqualTo("keycloak-sub-100");
            assertThat(res.getUsername()).isEqualTo("testuser");
            assertThat(res.getEmail()).isEqualTo("test@banking.local");
            assertThat(res.getProfileStatus()).isEqualTo(ProfileStatus.ACTIVE.name());

            Optional<UserProfile> saved = userProfileRepository.findByKeycloakUserId("keycloak-sub-100");
            assertThat(saved).isPresent();
        }

        @Test
        @DisplayName("Valid JWT with existing sub updates allowed identity attributes")
        void existingSub_shouldUpdateAttributes() {
            JwtAuthenticationToken token1 = createJwtToken("keycloak-sub-200", "user200", "old@banking.local", null, List.of("CUSTOMER"));
            userProfileService.getOrCreateCurrentUser(token1);

            JwtAuthenticationToken token2 = createJwtToken("keycloak-sub-200", "user200_updated", "new@banking.local", null, List.of("CUSTOMER"));
            UserProfileResponse res = userProfileService.getOrCreateCurrentUser(token2);

            assertThat(res.getEmail()).isEqualTo("new@banking.local");
            assertThat(res.getUsername()).isEqualTo("user200_updated");

            UserProfile profile = userProfileRepository.findByKeycloakUserId("keycloak-sub-200").orElseThrow();
            assertThat(profile.getEmail()).isEqualTo("new@banking.local");
        }

        @Test
        @DisplayName("Idempotent provisioning: two calls do not duplicate user")
        void multipleCalls_shouldBeIdempotent() {
            JwtAuthenticationToken token = createJwtToken("keycloak-sub-300", "user300", "user300@banking.local", null, List.of("CUSTOMER"));

            userProfileService.getOrCreateCurrentUser(token);
            userProfileService.getOrCreateCurrentUser(token);

            long count = userProfileRepository.count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Missing sub claim throws IllegalArgumentException")
        void missingSub_shouldFail() {
            Jwt jwt = Jwt.withTokenValue("mock")
                    .header("alg", "none")
                    .claim("preferred_username", "nosub")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();

            JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, List.of());

            assertThatThrownBy(() -> userProfileService.getOrCreateCurrentUser(token))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Branch assignment is preserved during routine identity sync")
        void branchPreservedDuringSync() {
            Branch branch = new Branch();
            branch.setBranchCode("BR-HCM-01");
            branch.setBranchName("HCM Central");
            branch.setStatus(BranchStatus.ACTIVE);
            branch = branchRepository.save(branch);

            JwtAuthenticationToken token = createJwtToken("keycloak-sub-400", "user400", "user400@bank.local", null, List.of("STAFF"));
            UserProfileResponse initial = userProfileService.getOrCreateCurrentUser(token);

            UserProfile profile = userProfileRepository.findById(initial.getUserId()).orElseThrow();
            profile.setBranch(branch);
            userProfileRepository.save(profile);

            // Re-sync with changed email
            JwtAuthenticationToken updateToken = createJwtToken("keycloak-sub-400", "user400", "changed@bank.local", null, List.of("STAFF"));
            UserProfileResponse synced = userProfileService.getOrCreateCurrentUser(updateToken);

            assertThat(synced.getBranch()).isNotNull();
            assertThat(synced.getBranch().getBranchCode()).isEqualTo("BR-HCM-01");
            assertThat(synced.getEmail()).isEqualTo("changed@bank.local");
        }
    }

    @Nested
    @DisplayName("Keycloak Group Synchronization Tests")
    class GroupSynchronizationTests {

        @Test
        @DisplayName("Valid groups claim creates group records and memberships")
        void validGroups_shouldCreateGroupRecords() {
            List<String> groups = List.of("/Customers", "/Retail/VIP");
            JwtAuthenticationToken token = createJwtToken("keycloak-sub-500", "user500", "user500@bank.local", groups, List.of("CUSTOMER"));

            UserProfileResponse res = userProfileService.getOrCreateCurrentUser(token);

            assertThat(res.getGroups()).containsExactlyInAnyOrder("/Customers", "/Retail/VIP");
            assertThat(identityGroupRepository.findByKeycloakGroupPath("/Customers")).isPresent();
            assertThat(identityGroupRepository.findByKeycloakGroupPath("/Retail/VIP")).isPresent();
        }

        @Test
        @DisplayName("Missing groups claim does NOT erase existing memberships")
        void missingGroupsClaim_shouldNotEraseMemberships() {
            // First login with groups
            JwtAuthenticationToken tokenWithGroups = createJwtToken("keycloak-sub-600", "user600", "user600@bank.local", List.of("/Staff/Tier1"), List.of("STAFF"));
            userProfileService.getOrCreateCurrentUser(tokenWithGroups);

            // Subsequent login where groups claim is completely absent
            JwtAuthenticationToken tokenWithoutGroups = createJwtToken("keycloak-sub-600", "user600", "user600@bank.local", null, List.of("STAFF"));
            UserProfileResponse res = userProfileService.getOrCreateCurrentUser(tokenWithoutGroups);

            assertThat(res.getGroups()).containsExactly("/Staff/Tier1");
        }

        @Test
        @DisplayName("Nested group paths are preserved as sync keys")
        void nestedGroupPathsPreserved() {
            List<String> nested = List.of("/Branches/South/Branch-02/LoanOfficers");
            JwtAuthenticationToken token = createJwtToken("keycloak-sub-700", "user700", "user700@bank.local", nested, List.of("STAFF"));

            UserProfileResponse res = userProfileService.getOrCreateCurrentUser(token);

            assertThat(res.getGroups()).containsExactly("/Branches/South/Branch-02/LoanOfficers");
            IdentityGroup group = identityGroupRepository.findByKeycloakGroupPath("/Branches/South/Branch-02/LoanOfficers").orElseThrow();
            assertThat(group.getGroupName()).isEqualTo("LoanOfficers");
        }
    }

    @Nested
    @DisplayName("Branch Management Tests")
    class BranchManagementTests {

        @Test
        @DisplayName("ADMIN can assign an active branch to user")
        void adminCanAssignActiveBranch() {
            Branch branch = new Branch();
            branch.setBranchCode("BR-HN-01");
            branch.setBranchName("Hanoi Branch");
            branch.setStatus(BranchStatus.ACTIVE);
            branch = branchRepository.save(branch);

            JwtAuthenticationToken staffToken = createJwtToken("keycloak-staff-1", "staff01", "staff@bank.local", null, List.of("STAFF"));
            UserProfileResponse userRes = userProfileService.getOrCreateCurrentUser(staffToken);

            Jwt adminJwt = Jwt.withTokenValue("admin.jwt")
                    .header("alg", "none")
                    .subject("admin-sub-999")
                    .claim("preferred_username", "admin01")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();

            BranchResponse response = branchService.assignBranch(userRes.getUserId(), branch.getId(), adminJwt);

            assertThat(response).isNotNull();
            assertThat(response.getBranchCode()).isEqualTo("BR-HN-01");

            UserProfile updated = userProfileRepository.findById(userRes.getUserId()).orElseThrow();
            assertThat(updated.getBranch().getId()).isEqualTo(branch.getId());
        }

        @Test
        @DisplayName("ADMIN assigning branch to their own profile is rejected")
        void adminCannotSelfAssignBranch() {
            Branch b = new Branch();
            b.setBranchCode("BR-DN-01");
            b.setBranchName("Da Nang");
            b.setStatus(BranchStatus.ACTIVE);
            final Branch branch = branchRepository.save(b);

            JwtAuthenticationToken adminToken = createJwtToken("admin-sub-123", "admin01", "admin@bank.local", null, List.of("ADMIN"));
            UserProfileResponse adminProfile = userProfileService.getOrCreateCurrentUser(adminToken);

            Jwt adminJwt = adminToken.getToken();

            assertThatThrownBy(() -> branchService.assignBranch(adminProfile.getUserId(), branch.getId(), adminJwt))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cannot assign a branch to their own profile");
        }

        @Test
        @DisplayName("Assigning inactive branch is rejected")
        void inactiveBranchAssignment_shouldFail() {
            Branch b = new Branch();
            b.setBranchCode("BR-OLD-01");
            b.setBranchName("Closed Branch");
            b.setStatus(BranchStatus.INACTIVE);
            final Branch branch = branchRepository.save(b);

            JwtAuthenticationToken userToken = createJwtToken("user-sub-888", "user888", "user888@bank.local", null, List.of("CUSTOMER"));
            UserProfileResponse userProfile = userProfileService.getOrCreateCurrentUser(userToken);

            Jwt adminJwt = Jwt.withTokenValue("admin.jwt")
                    .header("alg", "none")
                    .subject("admin-sub-000")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .build();

            assertThatThrownBy(() -> branchService.assignBranch(userProfile.getUserId(), branch.getId(), adminJwt))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("inactive");
        }
    }
}
