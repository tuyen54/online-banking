package com.onlinebanking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request body for administrative branch assignment.
 */
@Getter
@Setter
@NoArgsConstructor
public class BranchAssignmentRequest {

    @NotNull(message = "Branch ID is required")
    private UUID branchId;
}

