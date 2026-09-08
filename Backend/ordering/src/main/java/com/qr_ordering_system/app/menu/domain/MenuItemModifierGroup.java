package com.qr_ordering_system.app.menu.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "menu_item_modifier_groups", indexes = {
        @Index(name = "idx_mimg_tenant", columnList = "tenant_id"),
        @Index(name = "idx_mimg_menu_item", columnList = "menu_item_id"),
        @Index(name = "idx_mimg_modifier_group", columnList = "modifier_group_id")
})
@Getter @Setter
public class MenuItemModifierGroup extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modifier_group_id", nullable = false)
    private ModifierGroup modifierGroup;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "required_override")
    private Boolean requiredOverride;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
