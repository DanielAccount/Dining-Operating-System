package com.qr_ordering_system.app.orders.web;

import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.security.SecurityUser;
import com.qr_ordering_system.app.orders.dto.request.OrderCancelRequest;
import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderDetailResponse;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
import com.qr_ordering_system.app.orders.service.OrderStaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Staff-facing order endpoints (Phase 4), branch-scoped through the house
 * contract: pinned staff always operate on their own branch; tenant-wide
 * users must name one.
 *
 * <ul>
 *   <li>read (list/detail) needs ORDER_VIEW - waiters, cashiers, chefs too</li>
 *   <li>placing for a guest needs ORDER_CREATE - waiters and cashiers</li>
 *   <li>accepting a ticket needs ORDER_UPDATE - waiters and managers</li>
 *   <li>cancelling needs ORDER_CANCEL - every role holds it, and the reason
 *       is recorded on the order itself</li>
 * </ul>
 *
 * <p>The kitchen's item-level transitions (preparing/ready/served) are
 * deliberately absent: Phase 5's KDS owns them.</p>
 */
@RestController
@RequestMapping("api/branches/{branchId}")
@RequiredArgsConstructor
public class OrderController {

    private final OrderStaffService orderStaffService;

    @GetMapping("orders")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<List<OrderResponse>> list(@PathVariable Long branchId) {
        return ResponseEntity.ok(orderStaffService.list(branchId));
    }

    @GetMapping("orders/{orderId}")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<OrderDetailResponse> detail(
            @PathVariable Long branchId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(orderStaffService.detail(branchId, orderId));
    }

    /**
     * A waiter/cashier places an order for a guest at a table, by hand.
     * Same cart shape as the customer's, same engine, different source.
     */
    @PostMapping("tables/{tableId}/orders")
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    public ResponseEntity<OrderResponse> createAtTable(
            @PathVariable Long branchId,
            @PathVariable Long tableId,
            @Valid @RequestBody OrderSubmissionRequest request) {

        SecurityUser user = currentUser();
        OrderSource source = "CASHIER".equals(user.roleName())
                ? OrderSource.CASHIER
                : OrderSource.WAITER;

        OrderResponse order = orderStaffService.createAtTable(
                branchId, tableId, request, source, user.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * An action, not a state edit: POST because accepting twice is a 409,
     * and because the call mints the kitchen-facing invoice number.
     */
    @PostMapping("orders/{orderId}/accept")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    public ResponseEntity<OrderResponse> accept(
            @PathVariable Long branchId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(orderStaffService.accept(branchId, orderId));
    }

    @PostMapping("orders/{orderId}/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long branchId,
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) OrderCancelRequest request) {
        return ResponseEntity.ok(orderStaffService.cancel(
                branchId, orderId,
                request != null ? request.reason() : null,
                currentUser().userId()));
    }

    // ── helpers ────────────────────────────────────────────────────────

    /** Same principal-extraction pattern as MeController. */
    private SecurityUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof SecurityUser securityUser)) {
            // Unreachable behind ORDER_* guards (a missing principal has no
            // authorities), but fail closed rather than NPE on the impossible.
            throw new IllegalStateException("No authenticated staff user on a guarded endpoint");
        }
        return securityUser;
    }
}
