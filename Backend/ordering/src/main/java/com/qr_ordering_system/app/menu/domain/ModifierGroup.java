package com.qr_ordering_system.app.menu.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "modifier_groups", indexes = { @Index(name = "idx_modifier_group_tenant", columnList = "tenant_id") })
@Getter @Setter
@Check(constraints = "min_select <= max_select AND (NOT is_required OR min_select >= 1)")
public class ModifierGroup extends AbstractTenantEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_names", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> localizedNames = new HashMap<>();

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;

    @Column(name = "min_select", nullable = false)
    private Integer minSelect = 0;

    @Column(name = "max_select", nullable = false)
    private Integer maxSelect = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @OneToMany(mappedBy = "modifierGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModifierOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "modifierGroup")
    private List<MenuItemModifierGroup> menuItemLinks = new ArrayList<>();


}
