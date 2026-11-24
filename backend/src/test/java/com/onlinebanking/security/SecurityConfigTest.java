package com.onlinebanking.security;

import com.onlinebanking.entities.AuditLog;
import com.onlinebanking.repositories.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Spring Security configuration.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Endpoint-level RBAC rules (who can access what).</li>
 *   <li>401 response for unauthenticated requests.</li>
 *   <li>403 response for insufficient authorities.</li>
 *   <li>Consistent JSON error responses.</li>
 *   <li>Security audit events are recorded for 401 and 403.</li>
 *   <li>Full JWT → KeycloakRoleConverter → authorities flow.</li>
 * </ul>
 *
 * <p>Uses H2 in-memory database (see test application.yml).
 * The {@link JwtDecoder} is mocked to prevent contacting Keycloak during tests.
 *
 * <p><strong>Note:</strong> These tests use mock JWT authentication.
 * They do NOT verify real JWKS signature validation against a live Keycloak instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private com.onlinebanking.services.TransferService transferService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    // ═════════════════════════════════════════════════════════════
    // 1. Unauthenticated requests → 401
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unauthenticated requests (no JWT)")
    class UnauthenticatedTests {

        @Test
        @DisplayName("GET /api/admin/users without JWT → 401 with JSON body")
        void adminEndpointWithoutJwt_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.message").value("Authentication is required."))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("POST /api/transfers without JWT → 401")
        void transferEndpointWithoutJwt_shouldReturn401() throws Exception {
            mockMvc.perform(post("/api/transfers")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/audit-logs without JWT → 401")
        void auditLogsWithoutJwt_shouldReturn401() throws Exception {
            mockMvc.perform(get("/api/audit-logs"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 2. ADMIN role access
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ADMIN role")
    class AdminTests {

        @Test
        @DisplayName("ADMIN can access /api/admin/users")
        void adminCanAccessAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN can access /api/audit-logs")
        void adminCanAccessAuditLogs() throws Exception {
            mockMvc.perform(get("/api/audit-logs")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN is DENIED POST /api/transfers → 403")
        void adminCannotTransfer() throws Exception {
            mockMvc.perform(post("/api/transfers")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 3. CUSTOMER role access
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CUSTOMER role")
    class CustomerTests {

        @Test
        @DisplayName("CUSTOMER can POST /api/transfers")
        void customerCanTransfer() throws Exception {
            when(transferService.executeTransfer(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(com.onlinebanking.dto.TransferResponse.builder().transactionReference("TRF-TEST").build());

            String payload = """
                    {
                        "sourceAccountId": "00000000-0000-0000-0000-000000000001",
                        "destinationAccountNumber": "100123456789",
                        "amount": 10000.00,
                        "description": "Payment"
                    }
                    """;

            mockMvc.perform(post("/api/transfers")
                            .header("Idempotency-Key", "test-key-123")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("CUSTOMER can access /api/customers/me")
        void customerCanAccessOwnProfile() throws Exception {
            mockMvc.perform(get("/api/customers/me")
                            .with(jwt().jwt(builder -> builder
                                    .subject("customer-uuid-123")
                                    .claim("preferred_username", "customer01")
                                    .claim("email", "customer01@test.com"))
                                    .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value("customer-uuid-123"))
                    .andExpect(jsonPath("$.username").value("customer01"));
        }

        @Test
        @DisplayName("CUSTOMER is DENIED /api/admin/users → 403")
        void customerCannotAccessAdmin() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("CUSTOMER is DENIED /api/audit-logs → 403")
        void customerCannotAccessAuditLogs() throws Exception {
            mockMvc.perform(get("/api/audit-logs")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                    .andExpect(status().isForbidden());
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 4. STAFF role access
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("STAFF role")
    class StaffTests {

        @Test
        @DisplayName("STAFF is DENIED POST /api/transfers → 403")
        void staffCannotTransfer() throws Exception {
            mockMvc.perform(post("/api/transfers")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("STAFF is DENIED /api/admin/users → 403")
        void staffCannotAccessAdmin() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                    .andExpect(status().isForbidden());
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 5. AUDITOR role access
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AUDITOR role")
    class AuditorTests {

        @Test
        @DisplayName("AUDITOR can access /api/audit-logs (read-only)")
        void auditorCanAccessAuditLogs() throws Exception {
            mockMvc.perform(get("/api/audit-logs")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("AUDITOR is DENIED /api/admin/users → 403")
        void auditorCannotAccessAdmin() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AUDITOR is DENIED POST /api/transfers → 403")
        void auditorCannotTransfer() throws Exception {
            mockMvc.perform(post("/api/transfers")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AUDITOR")))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 6. Audit logging for 401 and 403
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Security audit logging")
    class AuditLoggingTests {

        @Test
        @DisplayName("401 creates an AUTHENTICATION_FAILURE audit record")
        void unauthenticatedRequest_shouldCreateAuditLog() throws Exception {
            mockMvc.perform(get("/api/admin/users"));

            List<AuditLog> logs = auditLogRepository.findByEventType("AUTHENTICATION_FAILURE");
            assertThat(logs).hasSize(1);

            AuditLog log = logs.get(0);
            assertThat(log.getStatusCode()).isEqualTo(401);
            assertThat(log.getHttpMethod()).isEqualTo("GET");
            assertThat(log.getRequestUri()).isEqualTo("/api/admin/users");
            assertThat(log.getUserId()).isNull(); // no authenticated user
            assertThat(log.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("403 creates an AUTHORIZATION_FAILURE audit record")
        void forbiddenRequest_shouldCreateAuditLog() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                    .with(jwt().jwt(builder -> builder
                                    .subject("customer-uuid-456")
                                    .claim("preferred_username", "customer01"))
                            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

            List<AuditLog> logs = auditLogRepository.findByEventType("AUTHORIZATION_FAILURE");
            assertThat(logs).hasSize(1);

            AuditLog log = logs.get(0);
            assertThat(log.getStatusCode()).isEqualTo(403);
            assertThat(log.getHttpMethod()).isEqualTo("GET");
            assertThat(log.getRequestUri()).isEqualTo("/api/admin/users");
            assertThat(log.getUserId()).isEqualTo("customer-uuid-456");
            assertThat(log.getUsername()).isEqualTo("customer01");
        }

        @Test
        @DisplayName("Successful requests do NOT create security audit records")
        void successfulRequest_shouldNotCreateAuditLog() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk());

            List<AuditLog> logs = auditLogRepository.findAll();
            assertThat(logs).isEmpty();
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 7. Full JWT → KeycloakRoleConverter integration
    // ═════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full JWT decoding + KeycloakRoleConverter flow")
    class JwtConverterIntegrationTests {

        @Test
        @DisplayName("Bearer token with realm_access.roles=[admin] → ADMIN access granted")
        void bearerTokenWithAdminRole_shouldGrantAccess() throws Exception {
            Jwt mockJwt = Jwt.withTokenValue("mock-bearer-token")
                    .header("alg", "RS256")
                    .subject("admin-user-uuid")
                    .claim("preferred_username", "admin01")
                    .claim("realm_access", Map.of("roles", List.of("admin")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            when(jwtDecoder.decode(anyString())).thenReturn(mockJwt);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer mock-bearer-token"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Bearer token with realm_access.roles=[customer] → ADMIN endpoint denied")
        void bearerTokenWithCustomerRole_shouldDenyAdminAccess() throws Exception {
            Jwt mockJwt = Jwt.withTokenValue("mock-bearer-token")
                    .header("alg", "RS256")
                    .subject("customer-user-uuid")
                    .claim("preferred_username", "customer01")
                    .claim("realm_access", Map.of("roles", List.of("customer")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            when(jwtDecoder.decode(anyString())).thenReturn(mockJwt);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer mock-bearer-token"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Bearer token with no realm_access → all role-protected endpoints denied")
        void bearerTokenWithNoRealmAccess_shouldDenyRoleProtectedEndpoints() throws Exception {
            Jwt mockJwt = Jwt.withTokenValue("mock-bearer-token")
                    .header("alg", "RS256")
                    .subject("unknown-user-uuid")
                    .claim("preferred_username", "unknown")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            when(jwtDecoder.decode(anyString())).thenReturn(mockJwt);

            mockMvc.perform(get("/api/admin/users")
                            .header("Authorization", "Bearer mock-bearer-token"))
                    .andExpect(status().isForbidden());
        }
    }
}

