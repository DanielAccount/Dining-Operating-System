package com.qr_ordering_system.app.orders.web;

import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
import com.qr_ordering_system.app.orders.service.PublicOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The public customer ORDER endpoints (Phase 4).
 *
 * <p>Same contract as {@code PublicTableController}: these paths sit under
 * SecurityConfig's {@code /api/public/**} permitAll, the QR slug is the only
 * credential, and dead slugs answer the same generic 404. The first
 * customer-writable endpoint in the app - protected by availability
 * re-validation, server-side pricing and the idempotency key rather than by
 * any login.</p>
 */
@RestController
@RequestMapping("api/public/tables")
@RequiredArgsConstructor
public class PublicOrderController {

    private final PublicOrderService publicOrderService;

    /**
     * Submit the phone's cart. 201 with the placed order (PENDING, priced,
     * snapshotted) - or 409 with a per-item reason when the kitchen's
     * reality moved under the cart, or 404 for a dead code.
     */
    @PostMapping("/{qrSlug}/orders")
    public ResponseEntity<OrderResponse> submit(
            @PathVariable String qrSlug,
            @Valid @RequestBody OrderSubmissionRequest request) {
        OrderResponse order = publicOrderService.submit(qrSlug, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * The table's orders for a phone holding the session code from a
     * previous submission. Unknown codes answer an empty list.
     */
    @GetMapping("/{qrSlug}/orders")
    public ResponseEntity<List<OrderResponse>> orders(
            @PathVariable String qrSlug,
            @RequestParam(required = false) String sessionCode) {
        return ResponseEntity.ok(publicOrderService.orders(qrSlug, sessionCode));
    }
}
