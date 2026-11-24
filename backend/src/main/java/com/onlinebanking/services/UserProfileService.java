package com.onlinebanking.services;

import com.onlinebanking.dto.UserProfileResponse;
import com.onlinebanking.entities.IdentityGroup;
import com.onlinebanking.entities.UserGroupMembership;
import com.onlinebanking.entities.UserProfile;
import com.onlinebanking.entities.enums.ProfileStatus;
import com.onlinebanking.repositories.IdentityGroupRepository;
import com.onlinebanking.repositories.UserGroupMembershipRepository;
import com.onlinebanking.repositories.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service managing local UserProfile entities, JIT provisioning,
 * and Keycloak group synchronization.
 */
@Service
public class UserProfileService {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository userProfileRepository;
    private final IdentityGroupRepository identityGroupRepository;
    private final UserGroupMembershipRepository userGroupMembershipRepository;
    private final AuditService auditService;

    public UserProfileService(UserProfileRepository userProfileRepository,
                              IdentityGroupRepository identityGroupRepository,
                              UserGroupMembershipRepository userGroupMembershipRepository,
                              AuditService auditService) {
        this.userProfileRepository = userProfileRepository;
        this.identityGroupRepository = identityGroupRepository;
        this.userGroupMembershipRepository = userGroupMembershipRepository;
        this.auditService = auditService;
    }

    /**
     * Obtains or provisions the banking profile for the authenticated Keycloak user.
     * Synchronizes allowlisted identity attributes and Keycloak groups if present in token.
     *
     * @param authentication Spring Security JwtAuthenticationToken
     * @return safe UserProfileResponse DTO
     */
    @Transactional
    public UserProfileResponse getOrCreateCurrentUser(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new IllegalArgumentException("Authentication and JWT token must not be null");
        }

        Jwt jwt = authentication.getToken();
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("JWT subject (sub) claim is missing or empty");
        }

        // 1. Provision or synchronize UserProfile
        UserProfile profile = syncUserProfile(jwt, sub);

        // 2. Synchronize Keycloak groups if "groups" claim is present
        syncKeycloakGroupsIfPresent(jwt, profile);

        // 3. Extract roles from authorities (strip ROLE_ prefix for presentation)
        List<String> roles = extractRoles(authentication);

        // 4. Retrieve current synchronized group paths
        List<String> groups = userGroupMembershipRepository.findByUserProfile(profile)
                .stream()
                .map(m -> m.getIdentityGroup().getKeycloakGroupPath())
                .sorted()
                .collect(Collectors.toList());

        return UserProfileResponse.from(profile, roles, groups);
    }

    /**
     * Idempotent & race-condition-aware user provisioning and identity attribute synchronization.
     */
    private UserProfile syncUserProfile(Jwt jwt, String sub) {
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        String displayName = jwt.getClaimAsString("name");
        Boolean emailVerified = jwt.getClaim("email_verified");

        if (displayName == null || displayName.isBlank()) {
            if (firstName != null || lastName != null) {
                displayName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            } else if (username != null) {
                displayName = username;
            }
        }

        UserProfile profile = userProfileRepository.findByKeycloakUserId(sub).orElse(null);

        if (profile == null) {
            // New User Provisioning
            profile = new UserProfile();
            profile.setKeycloakUserId(sub);
            profile.setUsername(username);
            profile.setEmail(email);
            profile.setFirstName(firstName);
            profile.setLastName(lastName);
            profile.setDisplayName(displayName);
            profile.setEmailVerified(emailVerified);
            profile.setProfileStatus(ProfileStatus.ACTIVE);
            profile.setLastSyncedAt(LocalDateTime.now());

            try {
                profile = userProfileRepository.save(profile);
                auditService.logSecurityEvent(
                        "USER_PROVISIONED",
                        sub,
                        username,
                        "GET",
                        "/api/users/me",
                        200,
                        null,
                        "User profile provisioned from Keycloak token for sub: " + sub
                );
            } catch (DataIntegrityViolationException ex) {
                // Concurrent provisioning attempt handled safely
                logger.info("Concurrent provisioning detected for keycloakUserId [{}], fetching existing record", sub);
                profile = userProfileRepository.findByKeycloakUserId(sub)
                        .orElseThrow(() -> new IllegalStateException("Failed to retrieve profile after concurrency conflict: " + sub));
            }
        } else {
            // Existing Profile: Synchronize allowed identity attributes
            boolean changed = false;

            if (!Objects.equals(profile.getUsername(), username)) {
                profile.setUsername(username);
                changed = true;
            }
            if (!Objects.equals(profile.getEmail(), email)) {
                profile.setEmail(email);
                changed = true;
            }
            if (!Objects.equals(profile.getFirstName(), firstName)) {
                profile.setFirstName(firstName);
                changed = true;
            }
            if (!Objects.equals(profile.getLastName(), lastName)) {
                profile.setLastName(lastName);
                changed = true;
            }
            if (!Objects.equals(profile.getDisplayName(), displayName)) {
                profile.setDisplayName(displayName);
                changed = true;
            }
            if (!Objects.equals(profile.getEmailVerified(), emailVerified)) {
                profile.setEmailVerified(emailVerified);
                changed = true;
            }

            // NOTE: Local branch and profileStatus are NEVER overwritten during sync
            profile.setLastSyncedAt(LocalDateTime.now());
            profile = userProfileRepository.save(profile);

            if (changed) {
                auditService.logSecurityEvent(
                        "USER_IDENTITY_SYNCHRONIZED",
                        sub,
                        username,
                        "GET",
                        "/api/users/me",
                        200,
                        null,
                        "User identity attributes synchronized for sub: " + sub
                );
            }
        }

        return profile;
    }

    /**
     * Synchronizes group memberships ONLY when the "groups" claim is present in the JWT.
     * If the claim is missing, does NOT clear existing group memberships.
     */
    private void syncKeycloakGroupsIfPresent(Jwt jwt, UserProfile profile) {
        if (!jwt.hasClaim("groups") || jwt.getClaim("groups") == null) {
            // Claim not present: do not erase existing memberships!
            return;
        }

        Object groupsClaim = jwt.getClaim("groups");
        List<String> tokenGroupPaths = new ArrayList<>();
        if (groupsClaim instanceof Collection<?> coll) {
            for (Object item : coll) {
                if (item instanceof String s && !s.isBlank()) {
                    tokenGroupPaths.add(s.trim());
                }
            }
        }

        // Fetch or create IdentityGroups for all token group paths
        Set<IdentityGroup> targetGroups = new HashSet<>();
        for (String path : tokenGroupPaths) {
            IdentityGroup group = identityGroupRepository.findByKeycloakGroupPath(path)
                    .orElseGet(() -> {
                        IdentityGroup newGroup = new IdentityGroup();
                        newGroup.setKeycloakGroupPath(path);
                        String simpleName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                        newGroup.setGroupName(simpleName.isBlank() ? path : simpleName);
                        return identityGroupRepository.save(newGroup);
                    });
            targetGroups.add(group);
        }

        List<UserGroupMembership> existingMemberships = userGroupMembershipRepository.findByUserProfile(profile);
        Set<IdentityGroup> existingGroups = existingMemberships.stream()
                .map(UserGroupMembership::getIdentityGroup)
                .collect(Collectors.toSet());

        boolean changed = false;

        // Add new memberships
        for (IdentityGroup group : targetGroups) {
            if (!existingGroups.contains(group)) {
                UserGroupMembership membership = new UserGroupMembership();
                membership.setUserProfile(profile);
                membership.setIdentityGroup(group);
                userGroupMembershipRepository.save(membership);
                changed = true;
            }
        }

        // Remove stale memberships (only when groups claim is explicitly present)
        for (UserGroupMembership membership : existingMemberships) {
            if (!targetGroups.contains(membership.getIdentityGroup())) {
                userGroupMembershipRepository.delete(membership);
                changed = true;
            }
        }

        if (changed) {
            auditService.logSecurityEvent(
                    "USER_GROUPS_CHANGED",
                    profile.getKeycloakUserId(),
                    profile.getUsername(),
                    "GET",
                    "/api/users/me",
                    200,
                    null,
                    "Keycloak group memberships updated for user: " + profile.getKeycloakUserId()
            );
        }
    }

    private List<String> extractRoles(JwtAuthenticationToken authentication) {
        if (authentication.getAuthorities() == null) {
            return Collections.emptyList();
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                .sorted()
                .collect(Collectors.toList());
    }
}

