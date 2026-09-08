package com.qr_ordering_system.app.common.tenant.repository;

import com.qr_ordering_system.app.common.tenant.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchRepository extends JpaRepository<Branch,Long> {
}
