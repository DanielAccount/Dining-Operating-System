package com.qr_ordering_system.app.orders.repository;

import com.qr_ordering_system.app.orders.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * The idempotency replay lookup: given the client's key, the order
     * that already exists for THIS tenant (@TenantId filters the rest).
     * A duplicate submission finds this instead of inserting.
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /** A table's orders, newest first - the customer "your orders" view. */
    List<Order> findByTableSessionIdOrderByCreatedAtDesc(Long tableSessionId);

    /** The branch's orders, newest first - the waiter/cashier list screen. */
    List<Order> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    /**
     * Staff-facing detail lookup, scoped to a branch: an order id that
     * belongs to another branch is a 404, not a 403 - same policy as every
     * other branch-scoped repository.
     */
    Optional<Order> findByIdAndBranchId(Long id, Long branchId);
}
