package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.Category;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem,Long> {
    List<MenuItem> findAllByCategoryId (Long categoryId);

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

}
