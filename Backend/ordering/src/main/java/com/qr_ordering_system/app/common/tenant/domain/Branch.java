package com.qr_ordering_system.app.common.tenant.domain;

import com.qr_ordering_system.app.common.domain.BranchStatus;
import com.qr_ordering_system.app.common.persistence.AbstractTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "branches", indexes = { @Index(name = "idx_branch_tenant", columnList = "tenant_id") })
@Getter
@Setter
public class Branch extends AbstractTenantEntity {

    @Column(nullable = false)
    private String name;

    private String address;
    private String contactNumber;

    @Column(columnDefinition = "varchar(64)")
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32) default 'ACTIVE'")
    private BranchStatus status = BranchStatus.ACTIVE;
}


