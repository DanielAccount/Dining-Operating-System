package com.qr_ordering_system.app.common.persistence;


import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@MappedSuperclass
@Getter @Setter
public abstract class AbstractTenantEntity extends BaseEntity{

    /**
     * The owning tenant.
     *
     * Field name is camelCase like every other field in the codebase - the
     * snake_case column is declared separately in @Column(name = ...). It used
     * to be called {@code tenant_id}, which produced getTenant_id()/setTenant_id()
     * at every call site and invited confusion with the column it maps.
     *
     * @TenantId makes Hibernate both filter reads by the current tenant and
     * populate this column on insert, so it must never be set by hand in normal
     * code (the dev seeder is the only exception).
     */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @ManyToOne
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;
}
