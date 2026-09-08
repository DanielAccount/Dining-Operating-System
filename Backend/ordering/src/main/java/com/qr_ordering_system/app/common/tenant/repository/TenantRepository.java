package com.qr_ordering_system.app.common.tenant.repository;

import com.qr_ordering_system.app.common.tenant.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
