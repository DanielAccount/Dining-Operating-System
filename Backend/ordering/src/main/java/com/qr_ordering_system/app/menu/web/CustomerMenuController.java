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
 * Staff-authenticated (MENU_VIEW): this is the branch menu the staff-side
 * "Diner Storefront" preview reads, and the path the Phase 2 availability
 * smoke tests exercised.
 *
 * The PUBLIC customer route lives in {@code PublicTableController} (Phase 3):
 * it resolves the branch from the QR slug, never touches
 * {@code BranchAccessResolver} or any caller-supplied branch id, and pins
 * {@code TenantContextHolder} from the resolved table before delegating to
 * the same {@code CustomerMenuService} - so the availability rules below are
 * the single source of truth for both audiences, and only "who may call"
 * differs between the two controllers.
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
