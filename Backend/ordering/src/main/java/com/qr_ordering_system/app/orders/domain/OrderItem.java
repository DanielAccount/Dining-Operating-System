package com.qr_ordering_system.app.orders.domain;

import com.qr_ordering_system.app.common.domain.OrderItemStatus;
import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One line of an order, with everything the kitchen and the customer saw
 * at submission time snapshotted onto the row.
 *
 * <p>{@code branchId} is a denormalized PLAIN column (not a relation) on
 * purpose: the KDS query pattern ("all PENDING/PREPARING items for this
 * branch, oldest first") is served by {@code idx_order_item_branch_status}
 * without a single join through Order.</p>
 *
 * <p>Item-level status exists so a fast drink is never hostage to a slow
 * burger on the same ticket: the kitchen progresses lines independently
 * (Phase 5 owns the transitions; Phase 4 just writes PENDING).</p>
 */
@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_item_tenant", columnList = "tenant_id"),
        @Index(name = "idx_order_item_order", columnList = "order_id"),
        @Index(name = "idx_order_item_menu_item", columnList = "menu_item_id"),
        // KDS query pattern - see the branchId javadoc.
        @Index(name = "idx_order_item_branch_status", columnList = "tenant_id, branch_id, item_status, created_at")
})
@Getter @Setter
public class OrderItem extends AbstractTenantEntity {

    /**
     * EAGER is required, not a choice: Hibernate 7 refuses LAZY to-one
     * associations to @SoftDelete entities. See the same comment on
     * BranchMenuItem.branch for the full story.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Denormalized from {@code order.branch} at creation time - plain
     * column, not a relationship, so the KDS query needs no join.
     */
    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.EAGER) // see the comment on `order`
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    /** Localized name at order time; menu edits later must not rewrite tickets. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_name_snapshot", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> itemNameSnapshot = new HashMap<>();

    @Column(nullable = false)
    private Integer quantity;

    /** Effective branch price (override or base) at the moment of submission. */
    @Column(name = "price_at_time_of_order", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAtTimeOfOrder;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, columnDefinition = "varchar(32)")
    private OrderItemStatus itemStatus = OrderItemStatus.PENDING;

    @Column(name = "ready_at", columnDefinition = "timestamptz")
    private OffsetDateTime readyAt;

    @Column(name = "served_at", columnDefinition = "timestamptz")
    private OffsetDateTime servedAt;

    // PERSIST/MERGE only - immutable audit rows; NO orphanRemoval, or an
    // in-memory list edit would fire permanent DELETEs on history.
    @OneToMany(mappedBy = "orderItem", cascade = { jakarta.persistence.CascadeType.PERSIST, jakarta.persistence.CascadeType.MERGE })
    private List<OrderItemModifier> selectedModifiers = new ArrayList<>();
}
