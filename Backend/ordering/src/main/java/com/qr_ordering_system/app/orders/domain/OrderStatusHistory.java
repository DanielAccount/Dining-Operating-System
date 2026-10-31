package com.qr_ordering_system.app.orders.domain;

import com.qr_ordering_system.app.common.domain.OrderStatus;
import com.qr_ordering_system.app.common.persistence.AbstractTenantAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One append-only entry in an order's status timeline: PENDING ->
 * ACCEPTED -> PREPARING -> READY -> COMPLETED (or CANCELLED).
 *
 * <p>An immutable audit row (see {@link AbstractTenantAuditableEntity}):
 * the current status lives on the order itself; this table is the story
 * of how it got there, so disputes have receipts.</p>
 */
@Entity
@Table(name = "order_status_history", indexes = {
        @Index(name = "idx_order_history_tenant", columnList = "tenant_id"),
        @Index(name = "idx_order_history_order", columnList = "order_id")
})
@Getter @Setter
public class OrderStatusHistory extends AbstractTenantAuditableEntity {

    /**
     * EAGER is required, not a choice: Hibernate 7 refuses LAZY to-one
     * associations to @SoftDelete entities. See the same comment on
     * BranchMenuItem.branch for the full story.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private OrderStatus status;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
