package com.onlinebanking.repositories;

import com.onlinebanking.entities.IdentityGroup;
import com.onlinebanking.entities.UserGroupMembership;
import com.onlinebanking.entities.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserGroupMembershipRepository extends JpaRepository<UserGroupMembership, UUID> {

    List<UserGroupMembership> findByUserProfile(UserProfile userProfile);

    Optional<UserGroupMembership> findByUserProfileAndIdentityGroup(UserProfile userProfile, IdentityGroup identityGroup);

    boolean existsByUserProfileAndIdentityGroup(UserProfile userProfile, IdentityGroup identityGroup);

    void deleteByUserProfile(UserProfile userProfile);
}
