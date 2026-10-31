package com.qr_ordering_system.app.orders.service;

import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.tenant.TenantContextHolder;
import com.qr_ordering_system.app.orders.dto.mapper.OrderMapper;
import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
import com.qr_ordering_system.app.orders.repository.OrderRepository;
import com.qr_ordering_system.app.tables.repository.TableSessionRepository;
import com.qr_ordering_system.app.tables.service.PublicTableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * The customer side of ordering (Phase 4): submit a cart by scanning a
 * table's QR code, and re-find "your" orders with the session code the
 * phone kept.
 *
 * <p>Same tenant discipline as {@code PublicTableMenuService} (read its
 * javadoc - the two classes share the pattern deliberately): this class is
 * NOT @Transactional, because the slug resolution must run tenant-less while
 * {@code OrderSubmissionService}'s transaction must open with the resolved
 * tenant already pinned. Resolve, pin, work, clear.</p>
 */
@Service
@RequiredArgsConstructor
public class PublicOrderService {

    private final PublicTableResolver publicTableResolver;
    private final OrderSubmissionService orderSubmissionService;
    private final TableSessionRepository tableSessionRepository;
    private final OrderRepository orderRepository;

    /**
     * Opens a short read transaction on demand - see {@link #orders}.
     * Never a class-level @Transactional here: the session must open AFTER
     * the tenant is pinned, not before (the class javadoc explains why).
     */
    private final TransactionTemplate transactionTemplate;

    /**
     * An anonymous customer submits a cart. The response carries the table's
     * {@code sessionCode} - the phone stores it and uses it for
     * {@link #orders} on later scans.
     */
    public OrderResponse submit(String qrSlug, OrderSubmissionRequest request) {
        PublicTableResolver.PublicTable resolved = publicTableResolver.resolve(qrSlug);

        // Pin BEFORE OrderSubmissionService opens its transaction.
        TenantContextHolder.setTenantId(resolved.table().getTenantId());
        try {
            return orderSubmissionService.submit(
                    resolved.branch(), resolved.table(), OrderSource.QR_CUSTOMER, null, request);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * The table's orders for a returning phone, newest first. Unknown or
     * missing session code answers an empty list, not an error: a fresh
     * phone scanning a table simply has no orders yet - and the code is
     * unguessable, so an empty list leaks nothing.
     */
    public List<OrderResponse> orders(String qrSlug, String sessionCode) {
        PublicTableResolver.PublicTable resolved = publicTableResolver.resolve(qrSlug);

        if (sessionCode == null || sessionCode.isBlank()) {
            return List.of();
        }

        TenantContextHolder.setTenantId(resolved.table().getTenantId());
        try {
            // The orders carry lazy `items` collections, and repository calls
            // without a transaction hand back DETACHED entities - mapping them
            // outside a session dies with LazyInitializationException. So the
            // read runs in one short transaction, opened AFTER the tenant is
            // pinned: the same pin-then-transaction discipline as inTenant()
            // in the test support, and of the submit path's engine call.
            return transactionTemplate.execute(unused -> tableSessionRepository
                    .findByTableIdAndSessionCode(resolved.table().getId(), sessionCode)
                    .map(session -> orderRepository
                            .findByTableSessionIdOrderByCreatedAtDesc(session.getId())
                            .stream()
                            .map(OrderMapper::toResponse)
                            .toList())
                    .orElse(List.of()));
        } finally {
            TenantContextHolder.clear();
        }
    }
}
