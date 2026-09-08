package com.qr_ordering_system.app.menu.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "modifier_options", indexes = {
        @Index(name = "idx_modifier_option_tenant", columnList = "tenant_id"),
        @Index(name = "idx_modifier_option_group", columnList = "modifier_group_id")
})
@Getter @Setter
@Check(constraints = "extra_price >= 0")
public class ModifierOption extends AbstractTenantEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "modifier_group_id", nullable = false)
    private ModifierGroup modifierGroup;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_names", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> localizedNames = new HashMap<>();

    @Column(name = "extra_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal extraPrice = BigDecimal.ZERO;

    @Column(name = "is_available", nullable = false)
    private Boolean isAvailable = true;

}
