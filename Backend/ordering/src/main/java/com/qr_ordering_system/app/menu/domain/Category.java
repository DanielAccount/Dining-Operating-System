package com.qr_ordering_system.app.menu.domain;

import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "categories", indexes = {
        @Index(name = "idx_category_tenant", columnList = "tenant_id"),
        @Index(name = "idx_category_display_order", columnList = "display_order")
})
@Getter @Setter
public class Category extends AbstractTenantEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "localized_names", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> localizedNames = new HashMap<>();

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

}
