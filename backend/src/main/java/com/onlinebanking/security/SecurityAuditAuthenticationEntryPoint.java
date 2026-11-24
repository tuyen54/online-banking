package com.onlinebanking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinebanking.dto.ErrorResponse;
import com.onlinebanking.services.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom {@link AuthenticationEntryPoint} that:
 * <ol>
 *   <li>Returns a consistent JSON 401 response (no stack traces, no token internals).</li>
 *   <li>Records the authentication failure in the security audit log.</li>
 * </ol>
 *
 * <p>Handles all authentication failures including:
 * <ul>
 *   <li>Missing Bearer token</li>
 *   <li>Invalid/expired JWT</li>
 *   <li>Invalid issuer or signature</li>
 * </ul>
 */
@Component
public class SecurityAuditAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditAuthenticationEntryPoint.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SecurityAuditAuthenticationEntryPoint(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // Determine a safe, sanitized reason — never expose internal JWT validation details
        String reason = categorizeFailure(authException);

        // Audit the event
        try {
            auditService.logSecurityEvent(
                    "AUTHENTICATION_FAILURE",
                    null,  // no authenticated user for a 401
                    null,
                    request.getMethod(),
                    request.getRequestURI(),
                    HttpServletResponse.SC_UNAUTHORIZED,
                    request.getRemoteAddr(),
                    reason
            );
        } catch (Exception e) {
            logger.error("Audit logging failed during 401 handling: {}", e.getMessage());
        }

        // Write JSON response
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication is required."
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    /**
     * Maps the exception to a human-readable category without leaking internals.
     */
    private String categorizeFailure(AuthenticationException ex) {
        if (ex == null) {
            return "Authentication required";
        }
        String message = ex.getMessage();
        if (message == null) {
            return "Authentication required";
        }

        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("expired")) {
            return "Token expired";
        }
        if (lowerMessage.contains("invalid") || lowerMessage.contains("malformed")) {
            return "Invalid token";
        }
        if (lowerMessage.contains("issuer")) {
            return "Invalid token issuer";
        }
        if (lowerMessage.contains("signature")) {
            return "Invalid token signature";
        }
        return "Authentication required";
    }
}

