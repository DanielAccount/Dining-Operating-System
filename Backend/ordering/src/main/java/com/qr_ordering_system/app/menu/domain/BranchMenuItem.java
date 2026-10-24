package com.qr_ordering_system.app.menu.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import com.qr_ordering_system.app.common.tenant.domain.Branch;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "branch_menu_items",
        indexes = {
                @Index(name = "idx_bmi_tenant", columnList = "tenant_id"),
                @Index(name = "idx_bmi_branch", columnList = "branch_id"),
                @Index(name = "idx_bmi_menu_item", columnList = "menu_item_id"),
                @Index(name = "idx_bmi_branch_stock", columnList = "branch_id, is_available, is_in_stock")
        })
@Getter @Setter
public class BranchMenuItem extends AbstractTenantEntity {

    // EAGER is required, not a choice: Hibernate 7 (the whole 7.2.x line,
    // verified against 7.2.7 and 7.2.12) refuses LAZY to-one associations whose
    // target is a @SoftDelete entity (everything extending BaseEntity is one):
    // a lazy proxy cannot apply the soft-delete filter at join time. This
    // restriction is why the backend stopped booting after the Boot 4 upgrade -
    // Phase 2's smoke runs predate it. The read paths that matter already
    // `join fetch` exactly these associations, so EAGER formalizes the
    // existing query shapes instead of changing them.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER) // see the comment on `branch`
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "price_override", precision = 19, scale = 2)
    private BigDecimal priceOverride;

    /**
     * Does THIS branch offer the item at all? False hides it from this
     * branch's customer menu entirely.
     */
    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

    /**
     * Does THIS branch have it right now? False keeps the item visible on the
     * customer menu but greyed out and unorderable ("sold out").
     *
     * Distinct from {@link #isAvailable}: "we don't serve this here" is a
     * permanent choice, "we're out of it today" is not. This is why stock
     * lives at branch level - one kitchen running out says nothing about
     * another.
     */
    @Column(name = "is_in_stock", nullable = false)
    private Boolean isInStock = true;

}
