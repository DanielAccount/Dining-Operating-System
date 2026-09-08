package com.qr_ordering_system.app.identity.repository;

import com.qr_ordering_system.app.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role,Long> {
    Optional<Role> findByTenantIdAndName (Long tenantId, String name);
}
