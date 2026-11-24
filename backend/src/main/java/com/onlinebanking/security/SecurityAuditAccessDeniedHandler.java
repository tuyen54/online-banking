package com.onlinebanking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinebanking.dto.ErrorResponse;
import com.onlinebanking.services.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom {@link AccessDeniedHandler} that:
 * <ol>
 *   <li>Returns a consistent JSON 403 response (no stack traces or sensitive data).</li>
 *   <li>Extracts the authenticated user's identity from the JWT and records
 *       the authorization failure in the security audit log.</li>
 * </ol>
 */
@Component
public class SecurityAuditAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditAccessDeniedHandler.class);

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SecurityAuditAccessDeniedHandler(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        // Extract user identity from the JWT (if available)
        String userId = null;
        String username = null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            userId = jwt.getSubject();
            username = jwt.getClaimAsString("preferred_username");
        }

        // Audit the event
        try {
            auditService.logSecurityEvent(
                    "AUTHORIZATION_FAILURE",
                    userId,
                    username,
                    request.getMethod(),
                    request.getRequestURI(),
                    HttpServletResponse.SC_FORBIDDEN,
                    request.getRemoteAddr(),
                    "Access denied: insufficient permissions"
            );
        } catch (Exception e) {
            logger.error("Audit logging failed during 403 handling: {}", e.getMessage());
        }

        // Write JSON response
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpServletResponse.SC_FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to access this resource."
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}

