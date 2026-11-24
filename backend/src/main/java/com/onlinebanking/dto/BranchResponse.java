package com.onlinebanking.dto;

import com.onlinebanking.entities.Branch;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * API response DTO for branch information.
 * Does not expose internal user data.
 */
@Getter
@Builder
@AllArgsConstructor
public class BranchResponse {

    private UUID id;
    private String branchCode;
    private String branchName;
    private String address;
    private String status;

    public static BranchResponse from(Branch branch) {
        return BranchResponse.builder()
                .id(branch.getId())
                .branchCode(branch.getBranchCode())
                .branchName(branch.getBranchName())
                .address(branch.getAddress())
                .status(branch.getStatus().name())
                .build();
    }
}

