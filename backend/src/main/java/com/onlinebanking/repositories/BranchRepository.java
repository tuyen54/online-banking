package com.onlinebanking.repositories;

import com.onlinebanking.entities.Branch;
import com.onlinebanking.entities.enums.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByBranchCode(String branchCode);

    List<Branch> findByStatus(BranchStatus status);
}

