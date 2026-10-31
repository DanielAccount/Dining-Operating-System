package com.qr_ordering_system.app.orders.repository;

import com.qr_ordering_system.app.orders.domain.OrderItemModifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only by convention: {@code OrderItemModifier} rows are immutable
 * audit records created through the order's cascade. This repository
 * exists for reads; nothing should ever saveOrUpdate through it.
 */
@Repository
public interface OrderItemModifierRepository extends JpaRepository<OrderItemModifier, Long> {

    List<OrderItemModifier> findByOrderItemId(Long orderItemId);
}
