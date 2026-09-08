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
                @Index(name = "idx_bmi_menu_item", columnList = "menu_item_id")
        })
@Getter @Setter
public class BranchMenuItem extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "price_override", precision = 19, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

}
