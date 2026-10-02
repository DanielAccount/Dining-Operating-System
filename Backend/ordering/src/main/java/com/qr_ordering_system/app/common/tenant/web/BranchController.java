package com.qr_ordering_system.app.common.tenant.web;

import com.qr_ordering_system.app.common.tenant.BranchContextHolder;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.dto.BranchSummaryResponse;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchRepository branchRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('BRANCH_VIEW')")
    public ResponseEntity<List<BranchSummaryResponse>> getAll() {
        // Branch-scoped staff are pinned to one branch by their token. Handing
        // them the whole tenant list told the UI about branches it could never
        // act on (the FE hid it, the API did not), so the narrowing happens
        // here - one place, instead of trusting every caller to remember.
        Long scopedBranchId = BranchContextHolder.getBranchId();

        List<Branch> branches = scopedBranchId != null
                ? branchRepository.findById(scopedBranchId).map(List::of).orElseGet(List::of)
                : branchRepository.findAllByOrderByNameAsc(); // tenant-filtered by @TenantId

        List<BranchSummaryResponse> body = branches.stream()
                .map(b -> new BranchSummaryResponse(b.getId(), b.getName(), b.getStatus().name()))
                .toList();
        return ResponseEntity.ok(body);
    }
}
