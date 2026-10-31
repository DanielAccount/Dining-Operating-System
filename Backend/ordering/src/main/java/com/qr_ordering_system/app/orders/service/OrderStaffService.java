package com.qr_ordering_system.app.orders.service;

import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.domain.OrderStatus;
import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.BranchAccessResolver;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.orders.domain.Order;
import com.qr_ordering_system.app.orders.domain.OrderStatusHistory;
import com.qr_ordering_system.app.orders.dto.mapper.OrderMapper;
import com.qr_ordering_system.app.orders.dto.request.OrderSubmissionRequest;
import com.qr_ordering_system.app.orders.dto.response.OrderDetailResponse;
import com.qr_ordering_system.app.orders.dto.response.OrderResponse;
import com.qr_ordering_system.app.orders.dto.response.OrderStatusHistoryResponse;
import com.qr_ordering_system.app.orders.repository.OrderRepository;
import com.qr_ordering_system.app.orders.repository.OrderStatusHistoryRepository;
import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.repository.TableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Staff-side orchestration around orders (Phase 4): the waiter/cashier
 * create path, the branch list, the detail-with-timeline view, and the
 * two status transitions Phase 4 owns - accept and cancel. The submission
 * math itself lives in {@link OrderSubmissionService}; this class decides
 * WHO placed the order and enforces branch scoping.
 *
 * <p>Status transitions are narrow on purpose. Phase 4 offers exactly
 * PENDING -> ACCEPTED and *- -> CANCELLED. PREPARING/READY/COMPLETED are
 * the kitchen's states and arrive with the KDS in Phase 5 - an order must
 * never reach COMPLETED by a path the kitchen never saw.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderStaffService {

    /** The states from which a cancel is still meaningful. */
    private static final Set<OrderStatus> CANCELLABLE =
            Set.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY);

    private final OrderSubmissionService orderSubmissionService;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderHistoryRepository;
    private final BranchAccessResolver branchAccessResolver;
    private final TableRepository tableRepository;

    /** A waiter (or cashier) places an order for a guest at a table, by hand. */
    public OrderResponse createAtTable(Long branchId,
                                       Long tableId,
                                       OrderSubmissionRequest request,
                                       OrderSource source,
                                       Long placedBy) {
        Branch branch = branchAccessResolver.resolve(branchId);

        TableEntity table = tableRepository.findByIdAndBranchId(tableId, branch.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));

        return orderSubmissionService.submit(branch, table, source, placedBy, request);
    }

    /** The branch's orders, newest first - the waiter/cashier working list. */
    @Transactional(readOnly = true)
    public List<OrderResponse> list(Long branchId) {
        Branch branch = branchAccessResolver.resolve(branchId);

        return orderRepository.findByBranchIdOrderByCreatedAtDesc(branch.getId()).stream()
                .map(OrderMapper::toResponse)
                .toList();
    }

    /** Order + full status timeline, branch-scoped (404, never 403, on other branches' ids). */
    @Transactional(readOnly = true)
    public OrderDetailResponse detail(Long branchId, Long orderId) {
        Branch branch = branchAccessResolver.resolve(branchId);
        Order order = requireOrder(branch.getId(), orderId);

        List<OrderStatusHistoryResponse> history = orderHistoryRepository
                .findByOrderIdOrderByIdAsc(order.getId()).stream()
                .map(h -> new OrderStatusHistoryResponse(
                        h.getId(),
                        h.getStatus() != null ? h.getStatus().name() : null,
                        h.getRemarks(),
                        h.getCreatedAt(),
                        h.getCreatedBy()))
                .toList();

        return new OrderDetailResponse(OrderMapper.toResponse(order), history);
    }

    /**
     * PENDING -> ACCEPTED: the waiter confirms the ticket, and the order
     * gets its human kitchen number. Invoice format is
     * {@code INV-<zero-padded order id>} - unique per tenant by
     * construction, race-free (no counter to coordinate), and stable.
     * Daily-resetting sequence numbers are deliberately NOT built: they
     * need per-tenant locking to be correct, and receipts are a Phase 7
     * concern; a wrong daily number is worse than a plain honest one.
     */
    public OrderResponse accept(Long branchId, Long orderId) {
        Branch branch = branchAccessResolver.resolve(branchId);
        Order order = requireOrder(branch.getId(), orderId);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PENDING orders can be accepted - this one is " + order.getStatus());
        }

        order.setStatus(OrderStatus.ACCEPTED);
        order.setInvoiceNumber("INV-%06d".formatted(order.getId()));
        record(order, OrderStatus.ACCEPTED, "Accepted by staff");

        return OrderMapper.toResponse(orderRepository.save(order));
    }

    /** Any live state -> CANCELLED, with who/when/why on the record. */
    public OrderResponse cancel(Long branchId, Long orderId, String reason, Long byUserId) {
        Branch branch = branchAccessResolver.resolve(branchId);
        Order order = requireOrder(branch.getId(), orderId);

        if (!CANCELLABLE.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This order is already " + order.getStatus() + " and can no longer be cancelled.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(OffsetDateTime.now());
        order.setCancelledBy(byUserId);
        order.setCancelReason(reason);
        record(order, OrderStatus.CANCELLED,
                reason != null ? "Cancelled: " + reason : "Cancelled by staff");

        return OrderMapper.toResponse(orderRepository.save(order));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private Order requireOrder(Long branchId, Long orderId) {
        return orderRepository.findByIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    /**
     * Appends one history row. {@code createdBy} is filled by the auditing
     * listener from the authenticated staff user - never set by hand here.
     */
    private void record(Order order, OrderStatus status, String remarks) {
        OrderStatusHistory entry = new OrderStatusHistory();
        entry.setOrder(order);
        entry.setStatus(status);
        entry.setRemarks(remarks);
        orderHistoryRepository.save(entry);
    }
}
