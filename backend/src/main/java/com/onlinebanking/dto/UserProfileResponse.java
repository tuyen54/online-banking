package com.onlinebanking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinebanking.entities.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * API response DTO for user profile information.
 *
 * <p>Combines local banking profile data with JWT-sourced roles
 * and synchronized Keycloak group memberships.
 *
 * <p>Never exposes passwords, tokens, or internal security configuration.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {

    private UUID userId;
    private String keycloakUserId;
    private String username;
    private String email;
    private String displayName;
    private String firstName;
    private String lastName;
    private Boolean emailVerified;
    private List<String> roles;
    private List<String> groups;
    private BranchResponse branch;
    private String profileStatus;

    /**
     * Build a response from a managed {@link UserProfile} entity,
     * validated JWT roles, and synchronized group memberships.
     */
    public static UserProfileResponse from(UserProfile profile,
                                            List<String> roles,
                                            List<String> groups) {
        BranchResponse branchResponse = null;
        if (profile.getBranch() != null) {
            branchResponse = BranchResponse.from(profile.getBranch());
        }

        return UserProfileResponse.builder()
                .userId(profile.getId())
                .keycloakUserId(profile.getKeycloakUserId())
                .username(profile.getUsername())
                .email(profile.getEmail())
                .displayName(profile.getDisplayName())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .emailVerified(profile.getEmailVerified())
                .roles(roles)
                .groups(groups)
                .branch(branchResponse)
                .profileStatus(profile.getProfileStatus().name())
                .build();
    }
}

