package com.qr_ordering_system.app.tables.web;

import com.qr_ordering_system.app.tables.dto.request.TableCreateRequest;
import com.qr_ordering_system.app.tables.dto.request.TableUpdateRequest;
import com.qr_ordering_system.app.tables.dto.response.TableResponse;
import com.qr_ordering_system.app.tables.service.TableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Table management for one branch (Phase 3).
 *
 * <p>Same branch contract as {@code BranchMenuItemController}:
 * branch-scoped users always operate on their own branch - the path
 * variable is ignored for them - and tenant-wide users (owner) must name
 * the branch explicitly. {@code BranchAccessResolver} enforces this inside
 * the service.</p>
 *
 * <p>The public customer side (scan QR → menu) is a deliberately separate
 * controller that never appears here: it has no branch path variable and
 * no authenticated user, so it resolves everything from the QR slug alone.
 * Phase 3 Milestone 2 builds it.</p>
 */
@RestController
@RequestMapping("api/branches/{branchId}/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    /**
     * TABLE_VIEW deliberately, not TABLE_MANAGE: waiters look up tables and
     * cashiers reconcile them against payments, and re-printing a damaged
     * QR tent is reading, not editing. The write endpoints below stay
     * TABLE_MANAGE.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('TABLE_VIEW')")
    public ResponseEntity<List<TableResponse>> list(@PathVariable Long branchId) {
        return ResponseEntity.ok(tableService.list(branchId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TABLE_MANAGE')")
    public ResponseEntity<TableResponse> create(
            @PathVariable Long branchId,
            @Valid @RequestBody TableCreateRequest request) {
        TableResponse response = tableService.create(branchId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{tableId}")
    @PreAuthorize("hasAuthority('TABLE_MANAGE')")
    public ResponseEntity<TableResponse> update(
            @PathVariable Long branchId,
            @PathVariable Long tableId,
            @Valid @RequestBody TableUpdateRequest request) {
        return ResponseEntity.ok(tableService.update(branchId, tableId, request));
    }

    @DeleteMapping("/{tableId}")
    @PreAuthorize("hasAuthority('TABLE_MANAGE')")
    public ResponseEntity<Void> delete(
            @PathVariable Long branchId,
            @PathVariable Long tableId) {
        tableService.delete(branchId, tableId);
        return ResponseEntity.noContent().build(); // 204 — success, nothing to return
    }

    /**
     * POST, not PUT: an action rather than a state edit - calling it twice
     * mints two different slugs, so it is non-idempotent by intent. Every
     * previously printed code for this table stops working.
     */
    @PostMapping("/{tableId}/regenerate-qr")
    @PreAuthorize("hasAuthority('TABLE_MANAGE')")
    public ResponseEntity<TableResponse> regenerateQr(
            @PathVariable Long branchId,
            @PathVariable Long tableId) {
        return ResponseEntity.ok(tableService.regenerateQrSlug(branchId, tableId));
    }
}
