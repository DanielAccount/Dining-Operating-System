package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.storage.FileStorageService;
import com.qr_ordering_system.app.menu.domain.Category;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.domain.MenuItemModifierGroup;
import com.qr_ordering_system.app.menu.domain.ModifierGroup;
import com.qr_ordering_system.app.menu.dto.mapper.MenuItemMapper;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemModifierGroupDTO;
import com.qr_ordering_system.app.menu.dto.request.MenuItemUpdateRequest;
import com.qr_ordering_system.app.menu.dto.response.MenuItemResponse;
import com.qr_ordering_system.app.menu.repository.CategoryRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemModifierGroupRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import com.qr_ordering_system.app.menu.repository.ModifierGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final MenuItemModifierGroupRepository menuItemModifierGroupRepository;
    private final FileStorageService fileStorageService;

    public MenuItemResponse create(MenuItemCreateRequest request) {
        // Validate category exists
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));

        MenuItem item = new MenuItem();
        item.setCategory(category);
        item.setLocalizedNames(new HashMap<>(request.localizedNames()));
        item.setLocalizedDescriptions(
                request.localizedDescriptions() != null
                        ? new HashMap<>(request.localizedDescriptions())
                        : new HashMap<>()
        );
        item.setBasePrice(request.basePrice());
        item.setImageUrl(request.imageUrl());
        item.setIsActive(request.isActive() != null ? request.isActive() : true);
        item.setDisplayOrder(resolveInsertPosition(category.getId(), request.displayOrder()));

        MenuItem saved = menuItemRepository.save(item);
        menuItemRepository.flush(); // apply the shift before returning the new row

        // Attach modifier groups if provided
        if (request.modifierGroups() != null && !request.modifierGroups().isEmpty()) {
            attachModifierGroups(saved, request.modifierGroups());
        }

        return MenuItemMapper.toResponse(menuItemRepository.save(saved));
    }

    public MenuItemResponse getById(Long id) {
        MenuItem item = menuItemRepository.findWithModifierGroupsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found: " + id));
        return MenuItemMapper.toResponse(item);
    }

    public List<MenuItemResponse> getAll() {
        return menuItemRepository.findAllWithDetails()
                .stream()
                .map(MenuItemMapper::toResponse)
                .toList();
    }

    public List<MenuItemResponse> getAllByCategory(Long categoryId) {
        return menuItemRepository.findAllWithDetailsByCategoryId(categoryId)
                .stream()
                .map(MenuItemMapper::toResponse)
                .toList();
    }

    public MenuItemResponse update(Long id, MenuItemUpdateRequest request) {
        MenuItem item = menuItemRepository.findWithModifierGroupsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found: " + id));

        Long oldCategoryId = item.getCategory().getId();
        boolean categoryChanged = !oldCategoryId.equals(request.categoryId());

        if (categoryChanged) {
            // Validate new category exists
            Category newCategory = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.categoryId()));
            moveToCategory(item, newCategory, oldCategoryId, request.displayOrder());
        } else if (request.displayOrder() != null) {
            repositionWithinCategory(item, request.displayOrder());
        }

        item.setLocalizedNames(new HashMap<>(request.localizedNames()));
        item.setLocalizedDescriptions(
                request.localizedDescriptions() != null
                        ? new HashMap<>(request.localizedDescriptions())
                        : new HashMap<>()
        );
        item.setBasePrice(request.basePrice());

        // File cleanup logic.
        //
        // The old file is only removed once this transaction has committed -
        // see deleteImageFileAfterCommit. Deleting it inline left the row
        // pointing at a file that no longer existed whenever the transaction
        // went on to roll back (an @Version clash on save, a constraint
        // violation on a modifier link), which is unrecoverable: the bytes are
        // gone and the URL still points at them.
        String oldImageUrl = item.getImageUrl();

        if (oldImageUrl != null && !oldImageUrl.equals(request.imageUrl())) {
            deleteImageFileAfterCommit(oldImageUrl);
        }

        item.setImageUrl(request.imageUrl());

        if (request.isActive() != null) {
            item.setIsActive(request.isActive());
        }

        MenuItem saved = menuItemRepository.save(item);

        // Reconcile modifier groups: remove old, add new
        if (request.modifierGroups() != null) {
            reconcileModifierGroups(saved, request.modifierGroups());
        }

        return MenuItemMapper.toResponse(menuItemRepository.save(saved));
    }

    public void delete(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found: " + id));

        // Delete associated image file from disk (after this transaction commits)
        deleteImageFileAfterCommit(item.getImageUrl());

        // Remove all modifier group links first
        List<MenuItemModifierGroup> links = menuItemModifierGroupRepository
                .findAllByMenuItemIdOrderByDisplayOrderAsc(id);
        links.forEach(menuItemModifierGroupRepository::delete);

        Long categoryId = item.getCategory().getId();
        int deletedOrder = item.getDisplayOrder();

        menuItemRepository.delete(item);
        // @SoftDelete turns this into an UPDATE, so the row must leave the
        // table before the neighbours are renumbered - otherwise the shift
        // would also decrement the row we are deleting.
        menuItemRepository.flush();
        menuItemRepository.shiftOrdersAfterDelete(categoryId, deletedOrder);
    }

    // ── Display order helpers ───────────────────────────────────────────────
    // Order is a dense 1..n sequence WITHIN a category, maintained with bulk
    // shift UPDATEs. Same trade-off as CategoryService: the shifts bypass
    // @Version, so two owners reordering the same category at the same instant
    // can interleave. Fine for menu management, not for a hot path.

    /**
     * Position for a brand-new item. null means "append at the end".
     */
    private int resolveInsertPosition(Long categoryId, Integer requested) {
        int maxOrder = menuItemRepository.findMaxDisplayOrder(categoryId);

        if (requested == null) {
            return maxOrder + 1;
        }

        // Clamp into [1, maxOrder + 1] so the sequence can never gain a hole.
        int target = Math.max(1, Math.min(requested, maxOrder + 1));
        menuItemRepository.shiftOrdersUp(categoryId, target, Integer.MAX_VALUE, -1L);
        return target;
    }

    /**
     * Moves an item to a new position inside the category it already belongs to.
     */
    private void repositionWithinCategory(MenuItem item, Integer requested) {
        int oldOrder = item.getDisplayOrder();
        if (requested.equals(oldOrder)) {
            return;
        }

        Long categoryId = item.getCategory().getId();
        int maxOrder = menuItemRepository.findMaxDisplayOrder(categoryId);
        int target = Math.max(1, Math.min(requested, maxOrder));

        if (target < oldOrder) {
            menuItemRepository.shiftOrdersUp(categoryId, target, oldOrder, item.getId());
        } else if (target > oldOrder) {
            menuItemRepository.shiftOrdersDown(categoryId, oldOrder, target, item.getId());
        }

        item.setDisplayOrder(target);
    }

    /**
     * Moves an item into a different category: compact the category it leaves,
     * then place it in the new one.
     *
     * The item is parked at order 0 and flushed first. That matters because
     * @Modifying queries do not flush pending changes, so without it the bulk
     * UPDATEs would still see the item sitting in its old category and would
     * renumber a row that is on its way out.
     */
    private void moveToCategory(MenuItem item, Category newCategory, Long oldCategoryId, Integer requested) {
        int oldOrder = item.getDisplayOrder();
        Long newCategoryId = newCategory.getId();

        item.setCategory(newCategory);
        item.setDisplayOrder(0); // out of range, so shifts cannot touch it
        menuItemRepository.saveAndFlush(item);

        // The item has left the old category in the DB, so this is safe now.
        menuItemRepository.shiftOrdersAfterDelete(oldCategoryId, oldOrder);

        item.setDisplayOrder(resolveInsertPosition(newCategoryId, requested));
    }

    // ── Private helpers ──

    /**
     * Removes an uploaded file, but only after the current transaction commits.
     *
     * The filesystem knows nothing about transactions: a rollback cannot put a
     * deleted file back. Doing this inline therefore meant any later failure in
     * the request (optimistic lock on {@code save}, a rejected modifier group,
     * a constraint violation) left a committed row referencing a file that had
     * already been unlinked. Registering an after-commit hook keeps the two in
     * step: either both happen, or neither does.
     */
    private void deleteImageFileAfterCommit(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            return;
        }

        // No transaction in progress (or synchronization disabled): there is
        // nothing to wait for, so delete straight away.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            fileStorageService.delete(fileName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.delete(fileName);
            }
        });
    }

    private void attachModifierGroups(MenuItem item, List<MenuItemModifierGroupDTO> dtos) {
        validateModifierGroupsExist(dtos);

        Set<Long> handled = new HashSet<>();
        int position = 0;

        for (MenuItemModifierGroupDTO dto : dtos) {
            if (!handled.add(dto.modifierGroupId())) {
                continue; // the same group was listed twice in the payload
            }
            position++;

            ModifierGroup group = modifierGroupRepository.findById(dto.modifierGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ModifierGroup not found: " + dto.modifierGroupId()));

            if (menuItemModifierGroupRepository.existsByMenuItemIdAndModifierGroupId(
                    item.getId(), dto.modifierGroupId())) {
                continue; // skip duplicates silently
            }

            MenuItemModifierGroup link = new MenuItemModifierGroup();
            link.setMenuItem(item);
            link.setModifierGroup(group);
            link.setDisplayOrder(linkPosition(dto.displayOrder(), position));
            link.setRequiredOverride(dto.requiredOverride());
            link.setIsActive(dto.isActive() != null ? dto.isActive() : true);
            menuItemModifierGroupRepository.save(link);
        }
    }

    private void reconcileModifierGroups(MenuItem item, List<MenuItemModifierGroupDTO> dtos) {
        validateModifierGroupsExist(dtos);

        // Current links, keyed by modifier group id.
        // merge() keeps the first entry: without it a duplicate key throws
        // IllegalStateException and surfaces as a 500.
        Map<Long, MenuItemModifierGroup> currentLinks = item.getModifierGroups().stream()
                .collect(Collectors.toMap(
                        mimg -> mimg.getModifierGroup().getId(),
                        mimg -> mimg,
                        (first, duplicate) -> first
                ));

        Set<Long> desiredIds = dtos.stream()
                .map(MenuItemModifierGroupDTO::modifierGroupId)
                .collect(Collectors.toSet());

        // Drop links that are no longer desired. orphanRemoval on the mappedBy
        // side deletes them when the entity manager flushes.
        item.getModifierGroups()
                .removeIf(mimg -> !desiredIds.contains(mimg.getModifierGroup().getId()));

        // Guard against the same group being listed twice in one payload.
        Set<Long> handled = new HashSet<>();
        int position = 0;

        for (MenuItemModifierGroupDTO dto : dtos) {
            if (!handled.add(dto.modifierGroupId())) {
                continue;
            }
            position++;

            MenuItemModifierGroup existing = currentLinks.get(dto.modifierGroupId());

            if (existing != null) {
                // Update the fields the caller actually supplied. A null
                // displayOrder means "keep the current position", so an
                // ordinary edit never reshuffles the item's modifier groups.
                if (dto.displayOrder() != null && dto.displayOrder() > 0) {
                    existing.setDisplayOrder(dto.displayOrder());
                }
                if (dto.requiredOverride() != null) {
                    existing.setRequiredOverride(dto.requiredOverride());
                }
                if (dto.isActive() != null) {
                    existing.setIsActive(dto.isActive());
                }
                menuItemModifierGroupRepository.save(existing);
            } else {
                // Create a new link.
                ModifierGroup group = modifierGroupRepository.findById(dto.modifierGroupId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "ModifierGroup not found: " + dto.modifierGroupId()));

                if (menuItemModifierGroupRepository.existsByMenuItemIdAndModifierGroupId(
                        item.getId(), dto.modifierGroupId())) {
                    continue;
                }

                MenuItemModifierGroup link = new MenuItemModifierGroup();
                link.setMenuItem(item);
                link.setModifierGroup(group);
                link.setDisplayOrder(linkPosition(dto.displayOrder(), position));
                link.setRequiredOverride(dto.requiredOverride());
                link.setIsActive(dto.isActive() != null ? dto.isActive() : true);
                menuItemModifierGroupRepository.save(link);
            }
        }
    }

    /**
     * Position for a modifier-group link within a menu item.
     *
     * 1-based, like every other display_order in the schema. Null or 0 (the old
     * default) means "you did not choose a position", so the group is appended
     * in the order the caller listed it - previously every link defaulted to 0
     * and the grid fell back to ordering by id.
     */
    private int linkPosition(Integer requested, int fallbackPosition) {
        return requested != null && requested > 0 ? requested : fallbackPosition;
    }

    private void validateModifierGroupsExist(List<MenuItemModifierGroupDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) return;

        Set<Long> ids = dtos.stream()
                .map(MenuItemModifierGroupDTO::modifierGroupId)
                .collect(Collectors.toSet());

        List<ModifierGroup> found = modifierGroupRepository.findAllById(ids);
        Set<Long> foundIds = found.stream().map(ModifierGroup::getId).collect(Collectors.toSet());

        for (Long id : ids) {
            if (!foundIds.contains(id)) {
                throw new ResourceNotFoundException("ModifierGroup not found: " + id);
            }
        }
    }
}