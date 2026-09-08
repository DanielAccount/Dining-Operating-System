package com.qr_ordering_system.app.identity.repository;

import com.qr_ordering_system.app.identity.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission,Long> {
    Optional<Permission> findByCode(String code);
}
