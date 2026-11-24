package com.onlinebanking.services;

import com.onlinebanking.dto.BranchResponse;
import com.onlinebanking.entities.Branch;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.BranchStatus;
import com.onlinebanking.repositories.BranchRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing Branch entities and administrative branch assignments.
 */
@Service
public class BranchService {

    private final BranchRepository branchRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuditService auditService;

    public BranchService(BranchRepository branchRepository,
                         UserProfileRepository userProfileRepository,
                         AuditService auditService) {
        this.branchRepository = branchRepository;
        this.userProfileRepository = userProfileRepository;
        this.auditService = auditService;
    }

    /**
     * Retrieve all active branches for lookup.
     */
    @Transactional(readOnly = true)
    public List<BranchResponse> getAllActiveBranches() {
        return branchRepository.findByStatus(BranchStatus.ACTIVE)
                .stream()
                .map(BranchResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Assign a branch to a user profile (ADMIN only operation).
     *
     * @param userProfileId target UserProfile UUID
     * @param branchId      target Branch UUID
     * @param adminJwt      admin caller's JWT token
     * @return updated BranchResponse
     */
    @Transactional
    public BranchResponse assignBranch(UUID userProfileId, UUID branchId, Jwt adminJwt) {
        String adminSub = adminJwt != null ? adminJwt.getSubject() : "UNKNOWN_ADMIN";
        String adminUsername = adminJwt != null ? adminJwt.getClaimAsString("preferred_username") : "unknown";

        // 1. Validate UserProfile exists
        UserProfile userProfile = userProfileRepository.findById(userProfileId)
                .orElseThrow(() -> {
                    auditService.logSecurityEvent(
                            "BRANCH_ASSIGNMENT_FAILED",
                            adminSub,
                            adminUsername,
                            "PATCH",
                            "/api/admin/users/" + userProfileId + "/branch",
                            404,
                            null,
                            "Failed branch assignment: UserProfile not found for ID " + userProfileId
                    );
                    return new IllegalArgumentException("User profile not found with ID: " + userProfileId);
                });

        // 2. Prevent self-assignment (security requirement: "Prevents users from assigning themselves")
        if (adminSub != null && adminSub.equals(userProfile.getKeycloakUserId())) {
            auditService.logSecurityEvent(
                    "BRANCH_ASSIGNMENT_FAILED",
                    adminSub,
                    adminUsername,
                    "PATCH",
                    "/api/admin/users/" + userProfileId + "/branch",
                    400,
                    null,
                    "Failed branch assignment: Administrator cannot assign branch to their own profile"
            );
            throw new IllegalStateException("Administrators cannot assign a branch to their own profile");
        }

        // 3. Validate Branch exists
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> {
                    auditService.logSecurityEvent(
                            "BRANCH_ASSIGNMENT_FAILED",
                            adminSub,
                            adminUsername,
                            "PATCH",
                            "/api/admin/users/" + userProfileId + "/branch",
                            404,
                            null,
                            "Failed branch assignment: Branch not found for ID " + branchId
                    );
                    return new IllegalArgumentException("Branch not found with ID: " + branchId);
                });

        // 4. Validate Branch is active (security requirement: "Rejects invalid or inactive branches")
        if (branch.getStatus() != BranchStatus.ACTIVE) {
            auditService.logSecurityEvent(
                    "BRANCH_ASSIGNMENT_FAILED",
                    adminSub,
                    adminUsername,
                    "PATCH",
                    "/api/admin/users/" + userProfileId + "/branch",
                    400,
                    null,
                    "Failed branch assignment: Cannot assign inactive branch " + branch.getBranchCode()
            );
            throw new IllegalStateException("Cannot assign an inactive branch: " + branch.getBranchCode());
        }

        Branch oldBranch = userProfile.getBranch();
        boolean isChange = oldBranch != null;
        userProfile.setBranch(branch);
        userProfileRepository.save(userProfile);

        String eventType = isChange ? "BRANCH_CHANGED" : "BRANCH_ASSIGNED";
        String description = String.format("User [%s] assigned to branch [%s] (code: %s) by admin [%s]",
                userProfile.getKeycloakUserId(), branch.getBranchName(), branch.getBranchCode(), adminSub);

        auditService.logSecurityEvent(
                eventType,
                adminSub,
                adminUsername,
                "PATCH",
                "/api/admin/users/" + userProfileId + "/branch",
                200,
                null,
                description
        );

        return BranchResponse.from(branch);
    }
}

