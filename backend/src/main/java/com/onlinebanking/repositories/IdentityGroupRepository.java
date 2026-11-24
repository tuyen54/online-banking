package com.onlinebanking.repositories;

import com.onlinebanking.entities.IdentityGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdentityGroupRepository extends JpaRepository<IdentityGroup, UUID> {

    Optional<IdentityGroup> findByKeycloakGroupPath(String keycloakGroupPath);
}
