package com.qr_ordering_system.app.common.tenant.repository;

import com.qr_ordering_system.app.common.tenant.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch,Long> {

    /**
     * Alphabetical, so the branch picker does not reshuffle between logins or
     * depend on insertion order. The @TenantId discriminator still applies.
     */
    List<Branch> findAllByOrderByNameAsc();
}
