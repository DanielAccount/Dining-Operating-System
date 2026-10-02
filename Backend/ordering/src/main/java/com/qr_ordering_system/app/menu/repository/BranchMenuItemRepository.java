package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.BranchMenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Overrides for one branch, restricted to a set of menu items.
     *
     * The bulk-save endpoint edits a handful of rows but used to load (and join
     * fetch) every row the branch has. This keeps the query proportional to the
     * payload instead of to the size of the menu.
     */
    @Query("""
               select bmi from BranchMenuItem bmi
               join fetch bmi.menuItem mi
               where bmi.branch.id = :branchId
                 and mi.id in :menuItemIds
               """)
    List<BranchMenuItem> findAllForBranchAndMenuItemIds(@Param("branchId") Long branchId,
                                                        @Param("menuItemIds") java.util.Collection<Long> menuItemIds);

    /**
     * Physically removes an override row.
     *
     * @SoftDelete would only flag it, and because
     * {@code uk_bmi_branch_menu_item} is a partial unique index
     * ({@code WHERE is_deleted = FALSE}) the next upsert is free to insert a
     * fresh row anyway - so a soft-deleted override is pure litter that grows by
     * one row per reset/upsert cycle on the busiest management screen. Nothing
     * references branch_menu_items historically (Phase 4 snapshots prices onto
     * the order line), so a real DELETE is safe here.
     *
     * A native query does NOT get the Hibernate tenant filter, so tenant_id is
     * matched explicitly. Callers pass the id from the row they already loaded,
     * never straight from the request.
     */
    @Modifying
    @Query(value = "DELETE FROM branch_menu_items WHERE id = :id AND tenant_id = :tenantId", nativeQuery = true)
    int hardDelete(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
