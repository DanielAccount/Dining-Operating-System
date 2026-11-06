package com.qr_ordering_system.app.orders.repository;

import com.qr_ordering_system.app.common.domain.OrderItemStatus;
import com.qr_ordering_system.app.orders.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** All lines of one order, insertion order (the id IS the timeline). */
    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    /**
     * The KDS query pattern this table's denormalized branchId exists for:
     * "give me everything still being worked on at this branch, oldest
     * first" - served by idx_order_item_branch_status, without joining
     * through Order.
     */
    List<OrderItem> findByBranchIdAndItemStatusInOrderByCreatedAtAscIdAsc(
            Long branchId, Collection<OrderItemStatus> statuses);

    /**
     * A kitchen action is scoped to the branch as well as the tenant. An
     * item id belonging to another branch is a 404, never a 403.
     */
    Optional<OrderItem> findByIdAndBranchId(Long id, Long branchId);
}
