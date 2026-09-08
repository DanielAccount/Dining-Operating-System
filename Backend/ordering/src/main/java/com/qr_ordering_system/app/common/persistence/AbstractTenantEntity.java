package com.qr_ordering_system.app.common.persistence;


import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@MappedSuperclass
@Getter @Setter
public abstract class AbstractTenantEntity extends BaseEntity{

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenant_id;

    @ManyToOne
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;
}
