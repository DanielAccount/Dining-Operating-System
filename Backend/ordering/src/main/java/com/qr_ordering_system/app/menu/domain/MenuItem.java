package com.qr_ordering_system.app.menu.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "menu_items", indexes = {
        @Index(name = "idx_menu_item_category", columnList = "category_id"),
        @Index(name = "idx_menu_item_tenant_active", columnList = "tenant_id, is_active"),
        @Index(name = "idx_menu_item_tenant_stock", columnList = "tenant_id, is_in_stock")
})
@Getter @Setter
public class MenuItem extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_names", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> localizedNames = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_descriptions", columnDefinition = "jsonb")
    private Map<String, String> localizedDescriptions = new HashMap<>();

    @Column(name = "base_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePrice;

    private String imageUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_in_stock", nullable = false)
    private Boolean isInStock = true;

    @OneToMany(mappedBy = "menuItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<MenuItemModifierGroup> modifierGroups = new ArrayList<>();

}
