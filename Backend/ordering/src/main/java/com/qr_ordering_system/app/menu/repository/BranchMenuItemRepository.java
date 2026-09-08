package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.BranchMenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchMenuItemRepository extends JpaRepository<BranchMenuItem, Long> {

    // Natural key lookup - enforces uk_bmi_branch_menu_item in code before the DB throws
    Optional<BranchMenuItem> findByBranchIdAndMenuItemId(Long branchId, Long menuItemId);

    boolean existsByBranchIdAndMenuItemId(Long branchId, Long menuItemId);

    // The "full menu for a branch" endpoint joins items + availability in one query
    @Query("""
               select bmi from BranchMenuItem bmi
               join fetch bmi.menuItem mi
               join fetch mi.category
               where bmi.branch.id = :branchId
               """)
    List<BranchMenuItem> findAllForBranch(@Param("branchId") Long branchId);
}
