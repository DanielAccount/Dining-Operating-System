package com.qr_ordering_system.app.common.tenant.domain;

import com.qr_ordering_system.app.common.domain.TenantStatus;
import com.qr_ordering_system.app.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenants", indexes = { @Index(name = "idx_tenant_business_name", columnList = "business_name") })
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(name = "business_name", nullable = false)
    private String businessName;

    private String contactNumber;
    private String address;

    @Column(nullable = false, columnDefinition = "varchar(64) default 'Asia/Yangon'")
    private String timezone = "Asia/Yangon";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32) default 'ACTIVE'")
    private TenantStatus status = TenantStatus.ACTIVE;
}
