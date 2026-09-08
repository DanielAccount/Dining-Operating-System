package com.qr_ordering_system.app.menu.repository;

import com.qr_ordering_system.app.menu.domain.MenuItemModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemModifierGroupRepository extends JpaRepository<MenuItemModifierGroup, Long> {

    // Enforces the same rule as your uk_mimg_menu_item_group unique index, in code
    Optional<MenuItemModifierGroup> findByMenuItemIdAndModifierGroupId(Long menuItemId, Long modifierGroupId);

    boolean existsByMenuItemIdAndModifierGroupId(Long menuItemId, Long modifierGroupId);

    // "Which items use this group?" - needed for safe modifier-group deletion
    List<MenuItemModifierGroup> findAllByModifierGroupId(Long modifierGroupId);

    List<MenuItemModifierGroup> findAllByMenuItemIdOrderByDisplayOrderAsc(Long menuItemId);
}
