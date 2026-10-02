package com.qr_ordering_system.app.common.tenant;

import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.common.tenant.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Resolves which branch a request is allowed to act on.
 *
 * Branch-scoped users (managers, cashiers, waiters, chefs) always operate on
 * their own branch, so the path variable is ignored for them. Tenant-wide users
 * (owner) must name the branch explicitly.
 *
 * This was private to BranchMenuItemService. The customer menu needs exactly
 * the same check, and duplicating a tenant-isolation check is how it ends up
 * being applied to one endpoint and forgotten on the next.
 */
@Component
@RequiredArgsConstructor
public class BranchAccessResolver {

    private final BranchRepository branchRepository;

    public Branch resolve(Long branchId) {
        Long contextBranchId = BranchContextHolder.getBranchId();
        Long effectiveId = contextBranchId != null ? contextBranchId : branchId;

        if (effectiveId == null) {
            throw new ResourceNotFoundException("Branch id is required");
        }

        Branch branch = branchRepository.findById(effectiveId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + effectiveId));

        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && !Objects.equals(branch.getTenantId(), tenantId)) {
            // 404 rather than 403: do not confirm that another tenant's branch exists.
            throw new ResourceNotFoundException("Branch not found: " + effectiveId);
        }

        return branch;
    }
}
