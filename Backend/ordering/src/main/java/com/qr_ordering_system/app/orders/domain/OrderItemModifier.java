package com.qr_ordering_system.app.orders.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantAuditableEntity;
import com.qr_ordering_system.app.menu.domain.ModifierOption;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One selected modifier option on one order line - an immutable audit
 * row (no version, no soft delete, no updated_at; see
 * {@link AbstractTenantAuditableEntity}).
 *
 * <p>Both the option relation AND the snapshots exist on purpose:
 * {@code modifierOption} is nullable because options get retired or
 * deleted from menus eventually - at which point the association reads
 * null while {@code modifierName}/{@code modifierPrice} keep telling the
 * kitchen exactly what "extra oat milk" meant on this ticket.</p>
 */
@Entity
@Table(name = "order_item_modifiers", indexes = {
        @Index(name = "idx_order_modifier_tenant", columnList = "tenant_id"),
        @Index(name = "idx_order_modifier_order_item", columnList = "order_item_id")
})
@Getter @Setter
public class OrderItemModifier extends AbstractTenantAuditableEntity {

    /**
     * EAGER is required, not a choice: Hibernate 7 refuses LAZY to-one
     * associations to @SoftDelete entities. See the same comment on
     * BranchMenuItem.branch for the full story.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /** Nullable on purpose - retired options null out, snapshots stay. */
    @ManyToOne(fetch = FetchType.EAGER) // see the comment on `orderItem`
    @JoinColumn(name = "modifier_option_id")
    private ModifierOption modifierOption;

    @Column(name = "modifier_name", nullable = false)
    private String modifierName;

    @Column(name = "modifier_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal modifierPrice;
}
