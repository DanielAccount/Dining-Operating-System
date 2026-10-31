package com.qr_ordering_system.app.orders.repository;

import com.qr_ordering_system.app.orders.domain.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only by convention: history rows are append-only audit records.
 * Writes happen when the order service records a transition; this
 * repository serves the timeline views.
 */
@Repository
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    /** The order's status story, in the order it happened. */
    List<OrderStatusHistory> findByOrderIdOrderByIdAsc(Long orderId);
}
