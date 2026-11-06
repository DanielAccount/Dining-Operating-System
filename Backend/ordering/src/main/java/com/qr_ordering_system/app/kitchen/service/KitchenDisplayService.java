package com.qr_ordering_system.app.kitchen.service;

import com.qr_ordering_system.app.common.domain.OrderItemStatus;
import com.qr_ordering_system.app.common.domain.OrderStatus;
import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.tenant.BranchAccessResolver;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.kitchen.dto.response.KitchenItemResponse;
import com.qr_ordering_system.app.kitchen.dto.response.KitchenModifierResponse;
import com.qr_ordering_system.app.orders.domain.Order;
import com.qr_ordering_system.app.orders.domain.OrderItem;
import com.qr_ordering_system.app.orders.domain.OrderStatusHistory;
import com.qr_ordering_system.app.orders.repository.OrderItemRepository;
import com.qr_ordering_system.app.orders.repository.OrderRepository;
import com.qr_ordering_system.app.orders.repository.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Phase 5 kitchen display orchestration.
 *
 * <p>The kitchen owns item-level progress. A drink can be ready while a
 * burger is still cooking, so each {@link OrderItem} advances independently:
 * {@code PENDING -> PREPARING -> READY -> SERVED}. The parent order keeps
 * the aggregate state of its slowest unfinished line, which means the
 * customer-facing order can never claim "served" before every line has
 * actually been served.</p>
 *
 * <p>Two rules protect the workflow:</p>
 * <ul>
 *   <li>an order must be ACCEPTED before any line can enter the kitchen;</li>
 *   <li>a stage cannot be skipped - a line goes to READY only from
 *       PREPARING, and to SERVED only from READY. Repeating the action that
 *       already succeeded is harmless and returns the current state, which
 *       makes a retried request safe on a flaky kitchen network.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class KitchenDisplayService {

    /** Lines still visible on the active KDS. SERVED lines leave the board. */
    private static final Set<OrderItemStatus> ACTIVE_ITEM_STATUSES =
            Set.of(OrderItemStatus.PENDING, OrderItemStatus.PREPARING, OrderItemStatus.READY);

    /** Only accepted work may enter the kitchen; PENDING and CANCELLED cannot. */
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES =
            Set.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY);

    private final BranchAccessResolver branchAccessResolver;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderHistoryRepository;

    /**
     * The branch's active kitchen lines, oldest first.
     *
     * <p>The item query uses the denormalized branch id and the existing
     * {@code idx_order_item_branch_status} index. PENDING orders are then
     * filtered out: their items also start in PENDING, but a waiter must
     * accept the ticket before the kitchen sees it.</p>
     */
    @Transactional(readOnly = true)
    public List<KitchenItemResponse> list(Long branchId) {
        Branch branch = branchAccessResolver.resolve(branchId);

        return orderItemRepository
                .findByBranchIdAndItemStatusInOrderByCreatedAtAscIdAsc(
                        branch.getId(), ACTIVE_ITEM_STATUSES)
                .stream()
                .filter(item -> item.getOrder() != null
                        && ACTIVE_ORDER_STATUSES.contains(item.getOrder().getStatus()))
                .map(this::toResponse)
                .toList();
    }

    public KitchenItemResponse markPreparing(Long branchId, Long itemId) {
        return transition(branchId, itemId, OrderItemStatus.PREPARING);
    }

    public KitchenItemResponse markReady(Long branchId, Long itemId) {
        return transition(branchId, itemId, OrderItemStatus.READY);
    }

    public KitchenItemResponse markServed(Long branchId, Long itemId) {
        return transition(branchId, itemId, OrderItemStatus.SERVED);
    }

    // ── transition engine ───────────────────────────────────────────────

    private KitchenItemResponse transition(Long branchId,
                                           Long itemId,
                                           OrderItemStatus target) {
        Branch branch = branchAccessResolver.resolve(branchId);
        OrderItem item = orderItemRepository.findByIdAndBranchId(itemId, branch.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found: " + itemId));

        Order order = item.getOrder();
        if (order == null) {
            throw new ResourceNotFoundException("Order item not found: " + itemId);
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            throw conflict(item, "This order has not been accepted by staff yet.");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw conflict(item, "This order was cancelled and cannot be processed.");
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw conflict(item, "This order is already complete.");
        }

        OrderItemStatus current = item.getItemStatus();
        if (current == target) {
            // A retry of a request that already succeeded is not a conflict.
            // Do not move ready_at/served_at again: those timestamps are facts,
            // not "last clicked at" values.
            return toResponse(item);
        }

        OrderItemStatus required = requiredPreviousStatus(target);
        if (current != required) {
            throw conflict(item, "This item is " + current
                    + " and must be " + required + " before it can be " + target + ".");
        }

        OffsetDateTime now = OffsetDateTime.now();
        item.setItemStatus(target);
        if (target == OrderItemStatus.READY && item.getReadyAt() == null) {
            item.setReadyAt(now);
        }
        if (target == OrderItemStatus.SERVED && item.getServedAt() == null) {
            item.setServedAt(now);
        }

        updateParentOrderStatus(order, item, target);
        return toResponse(item);
    }

    /**
     * Recomputes the parent order from every live line and records a history
     * entry only when the aggregate state actually changed.
     */
    private void updateParentOrderStatus(Order order,
                                         OrderItem changedItem,
                                         OrderItemStatus target) {
        List<OrderItem> allItems = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        List<OrderItemStatus> liveStatuses = allItems.stream()
                .map(OrderItem::getItemStatus)
                .filter(status -> status != OrderItemStatus.CANCELLED)
                .toList();

        if (liveStatuses.isEmpty()) {
            return;
        }

        OrderStatus aggregate;
        if (liveStatuses.stream().allMatch(status -> status == OrderItemStatus.SERVED)) {
            aggregate = OrderStatus.COMPLETED;
        } else if (liveStatuses.stream().allMatch(status ->
                status == OrderItemStatus.READY || status == OrderItemStatus.SERVED)) {
            aggregate = OrderStatus.READY;
        } else if (liveStatuses.stream().anyMatch(status ->
                status == OrderItemStatus.PREPARING
                        || status == OrderItemStatus.READY
                        || status == OrderItemStatus.SERVED)) {
            aggregate = OrderStatus.PREPARING;
        } else {
            aggregate = OrderStatus.ACCEPTED;
        }

        if (order.getStatus() == aggregate) {
            return;
        }

        order.setStatus(aggregate);
        orderRepository.save(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setStatus(aggregate);
        history.setRemarks(kitchenRemark(changedItem, target));
        orderHistoryRepository.save(history);
    }

    private String kitchenRemark(OrderItem item, OrderItemStatus target) {
        String action = switch (target) {
            case PREPARING -> "started preparing";
            case READY -> "marked ready";
            case SERVED -> "marked served";
            case PENDING, CANCELLED -> "changed";
        };
        return "Kitchen " + action + " item " + item.getId();
    }

    private OrderItemStatus requiredPreviousStatus(OrderItemStatus target) {
        return switch (target) {
            case PREPARING -> OrderItemStatus.PENDING;
            case READY -> OrderItemStatus.PREPARING;
            case SERVED -> OrderItemStatus.READY;
            case PENDING, CANCELLED -> throw new IllegalArgumentException(
                    "Unsupported kitchen transition target: " + target);
        };
    }

    private ResponseStatusException conflict(OrderItem item, String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private KitchenItemResponse toResponse(OrderItem item) {
        Order order = item.getOrder();
        List<KitchenModifierResponse> modifiers = item.getSelectedModifiers() == null
                ? List.of()
                : item.getSelectedModifiers().stream()
                    .sorted(Comparator.comparing(modifier -> modifier.getId()))
                    .map(modifier -> new KitchenModifierResponse(
                            modifier.getId(), modifier.getModifierName()))
                    .toList();

        return new KitchenItemResponse(
                item.getId(),
                order.getId(),
                order.getInvoiceNumber(),
                order.getTableNumberSnapshot(),
                order.getOrderType() != null ? order.getOrderType().name() : null,
                order.getSource() != null ? order.getSource().name() : null,
                order.getCustomerNote(),
                order.getCreatedAt(),
                item.getItemNameSnapshot(),
                item.getQuantity(),
                item.getItemStatus() != null ? item.getItemStatus().name() : null,
                order.getStatus() != null ? order.getStatus().name() : null,
                modifiers,
                item.getCreatedAt(),
                item.getReadyAt(),
                item.getServedAt());
    }
}
