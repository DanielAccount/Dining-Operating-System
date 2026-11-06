package com.qr_ordering_system.app.kitchen.web;

import com.qr_ordering_system.app.kitchen.dto.response.KitchenItemResponse;
import com.qr_ordering_system.app.kitchen.service.KitchenDisplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Branch-scoped kitchen display API (Phase 5).
 *
 * <p>Reading the board needs {@code KDS_VIEW}; changing a line needs
 * {@code KDS_UPDATE_ITEM_STATUS}. The branch is resolved by the service
 * through the shared branch-access rules: branch-pinned chefs always act on
 * their own branch, while a tenant-wide owner must name one.</p>
 */
@RestController
@RequestMapping("api/branches/{branchId}/kds")
@RequiredArgsConstructor
public class KitchenDisplayController {

    private final KitchenDisplayService kitchenDisplayService;

    @GetMapping
    @PreAuthorize("hasAuthority('KDS_VIEW')")
    public ResponseEntity<List<KitchenItemResponse>> list(@PathVariable Long branchId) {
        return ResponseEntity.ok(kitchenDisplayService.list(branchId));
    }

    @PostMapping("items/{itemId}/preparing")
    @PreAuthorize("hasAuthority('KDS_UPDATE_ITEM_STATUS')")
    public ResponseEntity<KitchenItemResponse> markPreparing(
            @PathVariable Long branchId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(kitchenDisplayService.markPreparing(branchId, itemId));
    }

    @PostMapping("items/{itemId}/ready")
    @PreAuthorize("hasAuthority('KDS_UPDATE_ITEM_STATUS')")
    public ResponseEntity<KitchenItemResponse> markReady(
            @PathVariable Long branchId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(kitchenDisplayService.markReady(branchId, itemId));
    }

    @PostMapping("items/{itemId}/served")
    @PreAuthorize("hasAuthority('KDS_UPDATE_ITEM_STATUS')")
    public ResponseEntity<KitchenItemResponse> markServed(
            @PathVariable Long branchId,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(kitchenDisplayService.markServed(branchId, itemId));
    }
}
