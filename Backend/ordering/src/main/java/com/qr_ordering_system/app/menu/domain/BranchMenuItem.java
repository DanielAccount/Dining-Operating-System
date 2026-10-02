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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
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
