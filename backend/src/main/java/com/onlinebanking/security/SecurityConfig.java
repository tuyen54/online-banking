package com.onlinebanking.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 6 configuration for the Online Banking API.
 *
 * <h3>Authentication</h3>
 * <ul>
 *   <li>OAuth 2.0 Resource Server with JWT (Keycloak-issued tokens).</li>
 *   <li>Stateless session management — no server-side sessions or cookies.</li>
 *   <li>CSRF disabled (API is exclusively Bearer-token authenticated, not cookie-based).</li>
 * </ul>
 *
 * <h3>Authorization (RBAC)</h3>
 * <ul>
 *   <li>{@code /api/admin/**} — ADMIN only.</li>
 *   <li>{@code /api/audit-logs/**} — ADMIN or AUDITOR.</li>
 *   <li>{@code POST /api/transfers} — CUSTOMER only (ADMIN and STAFF are explicitly denied).</li>
 *   <li>All other {@code /api/**} — any authenticated user (method-level rules refine access).</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <ul>
 *   <li>401 — handled by {@link SecurityAuditAuthenticationEntryPoint} (audit + JSON).</li>
 *   <li>403 — handled by {@link SecurityAuditAccessDeniedHandler} (audit + JSON).</li>
 * </ul>
 *
 * <h3>CORS</h3>
 * Explicit policy for the Angular frontend origin, configured via
 * the {@code app.cors.allowed-origins} property.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // enables @PreAuthorize, @PostAuthorize, @Secured
public class SecurityConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    private final SecurityAuditAuthenticationEntryPoint authenticationEntryPoint;
    private final SecurityAuditAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(SecurityAuditAuthenticationEntryPoint authenticationEntryPoint,
                          SecurityAuditAccessDeniedHandler accessDeniedHandler) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    // ─────────────────────────────────────────────────────────────
    // Security Filter Chain
    // ─────────────────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── CORS ──
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── CSRF disabled — API uses exclusively stateless Bearer tokens ──
            .csrf(csrf -> csrf.disable())

            // ── Stateless sessions ──
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // ── Endpoint-level RBAC ──
            .authorizeHttpRequests(auth -> auth
                // Admin-only endpoints
                .requestMatchers("/api/admin", "/api/admin/**").hasRole("ADMIN")

                // Audit logs — ADMIN or AUDITOR
                .requestMatchers("/api/audit-logs", "/api/audit-logs/**").hasAnyRole("ADMIN", "AUDITOR")

                // Transfers — CUSTOMER only (ADMIN and STAFF cannot transfer)
                .requestMatchers(HttpMethod.POST, "/api/transfers").hasRole("CUSTOMER")

                // Transactions history — CUSTOMER, STAFF, AUDITOR (ADMIN denied)
                .requestMatchers("/api/transactions", "/api/transactions/**").hasAnyRole("CUSTOMER", "STAFF", "AUDITOR")

                // Accounts — CUSTOMER only (STAFF, ADMIN, AUDITOR cannot access customer accounts)
                .requestMatchers("/api/accounts", "/api/accounts/**").hasRole("CUSTOMER")

                // All other API endpoints require authentication;
                // more specific authorization is enforced at method level
                .requestMatchers("/api/**").authenticated()

                // Non-API paths (health check, actuator, etc.) can be added here
                .anyRequest().permitAll()
            )

            // ── OAuth2 Resource Server — JWT ──
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )

            // ── Exception handling (fallback for non-oauth2 paths) ──
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }

    // ─────────────────────────────────────────────────────────────
    // JWT Authentication Converter
    // ─────────────────────────────────────────────────────────────

    /**
     * Configures how a validated JWT is converted into a Spring Security
     * {@link org.springframework.security.core.Authentication} object.
     *
     * <p>Uses {@link KeycloakRoleConverter} to extract realm roles from
     * the {@code realm_access.roles} claim and map them to
     * {@code ROLE_*} authorities.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return converter;
    }

    // ─────────────────────────────────────────────────────────────
    // CORS Configuration
    // ─────────────────────────────────────────────────────────────

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse comma-separated origins from configuration
        List<String> origins = Arrays.asList(allowedOrigins.split("\\s*,\\s*"));
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Idempotency-Key"
        ));

        configuration.setExposedHeaders(List.of("Authorization", "Idempotency-Key"));

        // Allow credentials (Bearer tokens via Authorization header)
        configuration.setAllowCredentials(true);

        // Cache preflight responses for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

