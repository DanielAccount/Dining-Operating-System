package com.qr_ordering_system.app.menu.web;

import com.qr_ordering_system.app.menu.dto.response.CustomerMenuCategoryResponse;
import com.qr_ordering_system.app.menu.service.CustomerMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only customer menu for a branch.
 *
 * Currently staff-authenticated (MENU_VIEW) purely so the availability rules
 * can be exercised end to end. Phase 3 re-exposes the same service through the
 * QR/table route with public access, scoped by table rather than by branch.
 * The availability logic lives in CustomerMenuService and will not change when
 * that happens - only who is allowed to call it.
 *
 * NOTE FOR PHASE 3: the public route cannot go through BranchAccessResolver,
 * which deliberately requires either a branch-scoped JWT or an explicit
 * branch id. A public caller has neither, so that route needs a variant that
 * takes the branch from the resolved QR slug, verifies the branch is ACTIVE,
 * and never consults TenantContextHolder (there is no tenant on a customer
 * request either).
 */
@RestController
@RequestMapping("api/branches/{branchId}/customer-menu")
@RequiredArgsConstructor
public class CustomerMenuController {

    private final CustomerMenuService customerMenuService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENU_VIEW')")
    public ResponseEntity<List<CustomerMenuCategoryResponse>> menu(@PathVariable Long branchId) {
        return ResponseEntity.ok(customerMenuService.menuForBranch(branchId));
    }
}
