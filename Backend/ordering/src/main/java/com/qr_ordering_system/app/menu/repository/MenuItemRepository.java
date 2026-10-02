package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.Category;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem,Long> {

    // Used in Step 4 rule: "can't deactivate/delete a category that still has items"
    long countByCategory(Category category);

    // Fetches modifier group links + groups in one go,
    // avoiding the N+1 on MenuItem.modifierGroups
    @Query("""
               select distinct mi from MenuItem mi
               left join fetch mi.modifierGroups mimg
               left join fetch mimg.modifierGroup
               where mi.id = :id
               """)
    Optional<MenuItem> findWithModifierGroupsById(@Param("id") Long id);

    @Query("""
               select mi from MenuItem mi
               join fetch mi.category c
               where mi.isActive = true
               order by c.displayOrder asc, mi.displayOrder asc, mi.id asc
               """)
    List<MenuItem> findAllActiveWithCategory();

    @Query("""
               select mi from MenuItem mi
               join fetch mi.category
               where mi.id = :id
               and mi.isActive = true
               """)
    Optional<MenuItem> findActiveWithCategoryById(@Param("id") Long id);

    @Query("""
               select mi from MenuItem mi
               join fetch mi.category
               where mi.id in :ids
               and mi.isActive = true
               """)
    List<MenuItem> findAllActiveWithCategoryByIdIn(@Param("ids") Collection<Long> ids);

    // ── Display order (per category) ──────────────────────────────────────
    // These mirror CategoryRepository: the caller renumbers the neighbours and
    // then persists the moved row. @Modifying bulk UPDATEs bypass @Version, so
    // concurrent reordering in the same category can interleave. Acceptable for
    // MVP menu management; a unique index on (category_id, display_order) would
    // not help because the shifts pass through conflicting intermediate states.

    // coalesce to 0 (not -1): item positions are 1-based, so an empty category
    // must report 0 for the "append" path to produce position 1.
    @Query("select coalesce(max(m.displayOrder), 0) from MenuItem m where m.category.id = :categoryId")
    int findMaxDisplayOrder(@Param("categoryId") Long categoryId);

    /** Increment orders in [startOrder, oldOrder) by 1. */
    @Modifying
    @Query("""
            UPDATE MenuItem m SET m.displayOrder = m.displayOrder + 1
            WHERE m.category.id = :categoryId
              AND m.displayOrder >= :startOrder
              AND m.displayOrder < :oldOrder
              AND m.id <> :menuItemId
            """)
    void shiftOrdersUp(@Param("categoryId") Long categoryId,
                       @Param("startOrder") int startOrder,
                       @Param("oldOrder") int oldOrder,
                       @Param("menuItemId") Long menuItemId);

    /** Decrement orders in (oldOrder, newOrder] by 1. */
    @Modifying
    @Query("""
            UPDATE MenuItem m SET m.displayOrder = m.displayOrder - 1
            WHERE m.category.id = :categoryId
              AND m.displayOrder > :oldOrder
              AND m.displayOrder <= :newOrder
              AND m.id <> :menuItemId
            """)
    void shiftOrdersDown(@Param("categoryId") Long categoryId,
                         @Param("oldOrder") int oldOrder,
                         @Param("newOrder") int newOrder,
                         @Param("menuItemId") Long menuItemId);

    /** Close the gap left by a deleted row. */
    @Modifying
    @Query("""
            UPDATE MenuItem m SET m.displayOrder = m.displayOrder - 1
            WHERE m.category.id = :categoryId
              AND m.displayOrder > :deletedOrder
            """)
    void shiftOrdersAfterDelete(@Param("categoryId") Long categoryId,
                                @Param("deletedOrder") int deletedOrder);

    /**
     * Items a customer could see, in menu order, with their modifier groups.
     *
     * Only is_active is filtered here - branch availability and branch stock are
     * per-branch and applied by the service once the branch overrides are merged
     * in, so this query stays branch-agnostic and reusable.
     *
     * modifierGroups is the only collection fetched; modifierGroup is a
     * ManyToOne, so there is no MultipleBagFetchException. Modifier options are
     * loaded separately rather than nested here, to avoid a cartesian product
     * across items x groups x options.
     */
    @Query("""
               select distinct mi from MenuItem mi
               join fetch mi.category c
               left join fetch mi.modifierGroups mimg
               left join fetch mimg.modifierGroup g
               where mi.isActive = true
               order by c.displayOrder asc, mi.displayOrder asc, mi.id asc
               """)
    List<MenuItem> findAllActiveForCustomerMenu();

    // ── Detail listings (menu management grid) ──────────────────────────────
    // modifierGroups is the only collection fetched here; category and
    // modifierGroup are ManyToOne, so there is no MultipleBagFetchException.

    @Query("""
               select distinct mi from MenuItem mi
               join fetch mi.category c
               left join fetch mi.modifierGroups mimg
               left join fetch mimg.modifierGroup
               order by c.displayOrder asc, mi.displayOrder asc, mi.id asc
               """)
    List<MenuItem> findAllWithDetails();

    @Query("""
               select distinct mi from MenuItem mi
               join fetch mi.category
               left join fetch mi.modifierGroups mimg
               left join fetch mimg.modifierGroup
               where mi.category.id = :categoryId
               order by mi.displayOrder asc, mi.id asc
               """)
    List<MenuItem> findAllWithDetailsByCategoryId(@Param("categoryId") Long categoryId);

}
