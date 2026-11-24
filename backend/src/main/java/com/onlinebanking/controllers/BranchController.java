package com.onlinebanking.controllers;

import com.onlinebanking.dto.BranchResponse;
import com.onlinebanking.services.BranchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for branch lookup. Returns non-sensitive branch details.
 */
@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    /**
     * Retrieve all active bank branches.
     */
    @GetMapping
    public ResponseEntity<List<BranchResponse>> getActiveBranches() {
        return ResponseEntity.ok(branchService.getAllActiveBranches());
    }
}

