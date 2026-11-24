package com.onlinebanking.controllers;

import com.onlinebanking.dto.UserProfileResponse;
import com.onlinebanking.services.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller providing current user profile operations.
 *
 * <p>Identity is derived strictly from the verified Keycloak JWT principal.
 * No user IDs are accepted from frontend request parameters.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * Retrieve the banking profile for the authenticated caller.
     * Automatically triggers JIT provisioning or identity attribute synchronization.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(JwtAuthenticationToken authentication) {
        UserProfileResponse response = userProfileService.getOrCreateCurrentUser(authentication);
        return ResponseEntity.ok(response);
    }
}

