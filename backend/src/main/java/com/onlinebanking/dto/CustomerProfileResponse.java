package com.onlinebanking.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.onlinebanking.entities.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Safe customer profile response DTO for profile access operations.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerProfileResponse {

    private UUID id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String displayName;
    private String profileStatus;
    private BranchResponse branch;
    private LocalDateTime createdAt;

    public static CustomerProfileResponse from(UserProfile profile) {
        BranchResponse branchResponse = null;
        if (profile.getBranch() != null) {
            branchResponse = BranchResponse.from(profile.getBranch());
        }

        return CustomerProfileResponse.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .displayName(profile.getDisplayName())
                .profileStatus(profile.getProfileStatus().name())
                .branch(branchResponse)
                .createdAt(profile.getCreatedAt())
                .build();
    }
}

