package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Long> {
    // For the menu management screen: ordered list for the UI
    List<Category> findAllByOrderByDisplayOrderAsc();

    @Query("select coalesce(max(c.displayOrder), -1) from Category c")
    int findMaxDisplayOrder();

    // Shift orders UP (increment display_order by +1) for range [from, to]
    @Modifying
    @Query("UPDATE Category c SET c.displayOrder = c.displayOrder + 1 WHERE c.displayOrder >= :startOrder AND c.displayOrder < :oldOrder AND c.id <> :categoryId")
    void shiftOrdersUp(@Param("startOrder") int startOrder, @Param("oldOrder") int oldOrder, @Param("categoryId") Long categoryId);

    // Shift orders DOWN (decrement display_order by -1) for range [from, to]
    @Modifying
    @Query("UPDATE Category c SET c.displayOrder = c.displayOrder - 1 WHERE c.displayOrder > :oldOrder AND c.displayOrder <= :newOrder AND c.id <> :categoryId")
    void shiftOrdersDown(@Param("oldOrder") int oldOrder, @Param("newOrder") int newOrder, @Param("categoryId") Long categoryId);

    // Shift all higher display orders down by 1 when a category is deleted
    @Modifying
    @Query("UPDATE Category c SET c.displayOrder = c.displayOrder - 1 WHERE c.displayOrder > :deletedOrder")
    void shiftOrdersAfterDelete(@Param("deletedOrder") int deletedOrder);
}
