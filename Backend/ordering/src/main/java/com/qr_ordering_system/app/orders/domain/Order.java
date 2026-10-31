package com.qr_ordering_system.app.orders.domain;

import com.qr_ordering_system.app.common.domain.OrderSource;
import com.qr_ordering_system.app.common.domain.OrderStatus;
import com.qr_ordering_system.app.common.domain.OrderType;
import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import com.qr_ordering_system.app.tables.domain.TableSession;
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
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One customer or waiter-submitted order (Phase 4, per the ERD's Order
 * Engine).
 *
 * <p>The money columns are all server-computed, and the database CHECKs
 * below are the contract: {@code final = subtotal - discount + tax +
 * service}. A client never sends amounts; it sends a cart, and this
 * entity's math is judged by the database itself.</p>
 *
 * <p>Idempotency: {@code (tenant_id, idempotency_key)} is unique via a
 * partial index, so a double-tapping phone cannot create two orders -
 * the second submission replays to the first (M2's job).</p>
 *
 * <p>{@code invoiceNumber} is minted when an order is first ACCEPTED -
 * the kitchen-facing human number. Abandoned PENDING orders never burn
 * sequence. (Phase 4 decision, confirmed before M1.)</p>
 */
@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_order_tenant_date", columnList = "tenant_id, created_at"),
                @Index(name = "idx_order_branch_date", columnList = "branch_id, created_at"),
                @Index(name = "idx_order_session", columnList = "table_session_id"),
                @Index(name = "idx_order_tenant_status", columnList = "tenant_id, status")
        }
        // uk_order_tenant_idempotency / uk_order_tenant_invoice -> V5 (partial, soft-delete safe)
)
@Getter @Setter
@Check(constraints = "(order_type = 'DINE_IN' AND table_session_id IS NOT NULL) OR " +
                     "(order_type = 'TAKEAWAY' AND table_session_id IS NULL)")
@Check(constraints = "subtotal >= 0 AND tax_amount >= 0 AND discount_amount >= 0 AND service_charge >= 0")
@Check(constraints = "final_amount = subtotal - discount_amount + tax_amount + service_charge")
public class Order extends AbstractTenantEntity {

    /**
     * EAGER is required, not a choice: Hibernate 7 refuses LAZY to-one
     * associations to @SoftDelete entities. See the same comment on
     * BranchMenuItem.branch for the full story.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** Required for DINE_IN, must be NULL for TAKEAWAY - the DB CHECK enforces it. */
    @ManyToOne(fetch = FetchType.EAGER) // see the comment on `branch`
    @JoinColumn(name = "table_session_id")
    private TableSession tableSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, columnDefinition = "varchar(32)")
    private OrderType orderType = OrderType.DINE_IN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private OrderSource source = OrderSource.QR_CUSTOMER;

    /**
     * Who placed it: null or a system marker for QR customers, the staff
     * user id for waiter/cashier orders. A plain id, not a relation - user
     * rows may retire, but the record of who took the order must not fade.
     */
    @Column(name = "placed_by")
    private Long placedBy;

    /** The table label at order time; renumbering tables later must not rewrite tickets. */
    @Column(name = "table_number_snapshot", length = 64)
    private String tableNumberSnapshot;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Column(name = "customer_phone_number", length = 64)
    private String customerPhoneNumber;

    /** Client-generated (UUID); unique per tenant; M2 replays duplicates. */
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    /** Human kitchen number, minted on first ACCEPTED. Null until then. */
    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32)")
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "cancelled_at", columnDefinition = "timestamptz")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    // ── Money: all server-computed, CHECK-guarded ──────────────────

    @Column(nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "service_charge", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 19, scale = 2, columnDefinition = "numeric(19,2) default 0")
    private BigDecimal finalAmount = BigDecimal.ZERO;

    // ── Children ─────────────────────────────────────────────────────
    //
    // PERSIST/MERGE only - NO orphanRemoval: order lines are historical
    // records; dropping one from this list in memory must never fire a
    // delete. Cancelling is a status, not a row removal.

    @OneToMany(mappedBy = "order", cascade = { jakarta.persistence.CascadeType.PERSIST, jakarta.persistence.CascadeType.MERGE })
    private List<OrderItem> items = new ArrayList<>();
}
