package com.onlinebanking.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KeycloakRoleConverter}.
 *
 * <p>These are pure unit tests — no Spring context, no Keycloak connection.
 * They verify that the converter correctly extracts {@code realm_access.roles}
 * from a JWT and maps them to Spring Security {@code ROLE_*} authorities.
 */
class KeycloakRoleConverterTest {

    private KeycloakRoleConverter converter;

    @BeforeEach
    void setUp() {
        converter = new KeycloakRoleConverter();
    }

    // ─────────────────────────────────────────────────────────────
    // Happy-path: each of the four business roles
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Role mapping for all four business roles")
    class RoleMappingTests {

        @Test
        @DisplayName("customer → ROLE_CUSTOMER")
        void shouldConvertCustomerRole() {
            Jwt jwt = jwtWithRoles(List.of("customer"));
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).containsExactly(
                    new SimpleGrantedAuthority("ROLE_CUSTOMER")
            );
        }

        @Test
        @DisplayName("admin → ROLE_ADMIN")
        void shouldConvertAdminRole() {
            Jwt jwt = jwtWithRoles(List.of("admin"));
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).containsExactly(
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );
        }

        @Test
        @DisplayName("staff → ROLE_STAFF")
        void shouldConvertStaffRole() {
            Jwt jwt = jwtWithRoles(List.of("staff"));
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).containsExactly(
                    new SimpleGrantedAuthority("ROLE_STAFF")
            );
        }

        @Test
        @DisplayName("auditor → ROLE_AUDITOR")
        void shouldConvertAuditorRole() {
            Jwt jwt = jwtWithRoles(List.of("auditor"));
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).containsExactly(
                    new SimpleGrantedAuthority("ROLE_AUDITOR")
            );
        }

        @Test
        @DisplayName("Multiple roles are all converted")
        void shouldConvertMultipleRoles() {
            Jwt jwt = jwtWithRoles(List.of("admin", "auditor"));
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).containsExactlyInAnyOrder(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_AUDITOR")
            );
        }

        @Test
        @DisplayName("Roles are uppercased consistently")
        void shouldUppercaseRoles() {
            Jwt jwt = jwtWithRoles(List.of("Customer", "ADMIN", "Staff"));
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).containsExactlyInAnyOrder(
                    new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_STAFF")
            );
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Missing, null, or malformed claims
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing or malformed realm_access claims")
    class MalformedClaimTests {

        @Test
        @DisplayName("Missing realm_access claim → empty authorities")
        void shouldReturnEmptyWhenRealmAccessMissing() {
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "RS256")
                    .subject("user-123")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            Collection<GrantedAuthority> authorities = converter.convert(jwt);
            assertThat(authorities).isEmpty();
        }

        @Test
        @DisplayName("realm_access without roles key → empty authorities")
        void shouldReturnEmptyWhenRolesKeyMissing() {
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "RS256")
                    .subject("user-123")
                    .claim("realm_access", Map.of("other_key", "value"))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            Collection<GrantedAuthority> authorities = converter.convert(jwt);
            assertThat(authorities).isEmpty();
        }

        @Test
        @DisplayName("Empty roles list → empty authorities")
        void shouldReturnEmptyWhenRolesListEmpty() {
            Jwt jwt = jwtWithRoles(Collections.emptyList());
            Collection<GrantedAuthority> authorities = converter.convert(jwt);
            assertThat(authorities).isEmpty();
        }

        @Test
        @DisplayName("roles is not a collection (String instead) → empty authorities")
        void shouldReturnEmptyWhenRolesIsNotCollection() {
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "RS256")
                    .subject("user-123")
                    .claim("realm_access", Map.of("roles", "not-a-list"))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            Collection<GrantedAuthority> authorities = converter.convert(jwt);
            assertThat(authorities).isEmpty();
        }

        @Test
        @DisplayName("Non-string entries in roles list are safely skipped")
        void shouldSkipNonStringRoleEntries() {
            // Mix of valid String roles and invalid entries
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "RS256")
                    .subject("user-123")
                    .claim("realm_access", Map.of("roles", List.of("admin", 42, "customer")))
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();

            Collection<GrantedAuthority> authorities = converter.convert(jwt);
            assertThat(authorities).containsExactlyInAnyOrder(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_CUSTOMER")
            );
        }

        @Test
        @DisplayName("Blank role strings are filtered out")
        void shouldFilterBlankRoleStrings() {
            Jwt jwt = jwtWithRoles(List.of("admin", "", "  ", "customer"));
            Collection<GrantedAuthority> authorities = converter.convert(jwt);

            assertThat(authorities).containsExactlyInAnyOrder(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_CUSTOMER")
            );
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────

    private Jwt jwtWithRoles(List<String> roles) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject("test-user-id")
                .claim("preferred_username", "testuser")
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }
}

