package com.qr_ordering_system.app.menu.web;

import com.qr_ordering_system.app.menu.dto.request.BranchMenuItemBulkUpsertRequest;
import com.qr_ordering_system.app.menu.dto.request.BranchMenuItemUpsertRequest;
import com.qr_ordering_system.app.menu.dto.response.BranchMenuItemResponse;
import com.qr_ordering_system.app.menu.service.BranchMenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Branch pricing & availability slice.
 *
 * Branch-scoped users (branchId in their JWT) always operate on their own
 * branch — the path variable is treated as optional for them. Tenant-wide
 * users (owner) pass the branch explicitly.
 */
@RestController
@RequestMapping("api/branches/{branchId}/menu-items")
@RequiredArgsConstructor
public class BranchMenuItemController {

    private final BranchMenuItemService branchMenuItemService;

    @GetMapping
    @PreAuthorize("hasAuthority('MENU_VIEW')")
    public ResponseEntity<List<BranchMenuItemResponse>> list(@PathVariable Long branchId) {
        return ResponseEntity.ok(branchMenuItemService.listForBranch(branchId));
    }

    @PutMapping("/{menuItemId}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<BranchMenuItemResponse> upsert(
            @PathVariable Long branchId,
            @PathVariable Long menuItemId,
            @Valid @RequestBody BranchMenuItemUpsertRequest request) {
        return ResponseEntity.ok(branchMenuItemService.upsert(branchId, menuItemId, request));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<List<BranchMenuItemResponse>> bulkUpsert(
            @PathVariable Long branchId,
            @Valid @RequestBody BranchMenuItemBulkUpsertRequest request) {
        return ResponseEntity.ok(branchMenuItemService.bulkUpsert(branchId, request));
    }

    @DeleteMapping("/{menuItemId}")
    @PreAuthorize("hasAuthority('MENU_MANAGE')")
    public ResponseEntity<Void> reset(
            @PathVariable Long branchId,
            @PathVariable Long menuItemId) {
        branchMenuItemService.reset(branchId, menuItemId);
        return ResponseEntity.noContent().build();
    }
}
