package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.common.storage.FileStorageService;
import com.qr_ordering_system.app.menu.domain.Category;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.domain.MenuItemModifierGroup;
import com.qr_ordering_system.app.menu.dto.mapper.MenuItemMapper;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemUpdateRequest;
import com.qr_ordering_system.app.menu.dto.response.MenuItemResponse;
import com.qr_ordering_system.app.menu.repository.CategoryRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemModifierGroupRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create, read, update and delete for menu items.
 *
 * <p>This class deliberately does one job per public method, and pushes the two
 * fiddly concerns into collaborators that can each be read on their own:
 * <ul>
 *   <li>{@link MenuItemDisplayOrderManager} - where the item sits in its
 *       category and which neighbours must shift to make room</li>
 *   <li>{@link MenuItemModifierLinkService} - which modifier groups this item
 *       offers, and with what per-item settings</li>
 *   <li>{@link FileStorageService#deleteAfterCommit} - removing the old image
 *       file safely when the URL changes</li>
 * </ul>
 *
 * <p>To follow a request end to end, read {@link #create} or {@link #update} -
 * each is a short, named list of steps.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final MenuItemModifierGroupRepository linkRepository;
    private final FileStorageService fileStorageService;
    private final MenuItemDisplayOrderManager displayOrder;
    private final MenuItemModifierLinkService modifierLinks;

    // ── Create ────────────────────────────────────────────────────────────

    /**
     * Adds a new item to the end of (or at a chosen position in) a category.
     */
    public MenuItemResponse create(MenuItemCreateRequest request) {
        Category category = requireCategory(request.categoryId());

        MenuItem item = new MenuItem();
        item.setCategory(category);
        item.setLocalizedNames(new HashMap<>(request.localizedNames()));
        item.setLocalizedDescriptions(copyOrEmpty(request.localizedDescriptions()));
        item.setBasePrice(request.basePrice());
        item.setImageUrl(request.imageUrl());
        item.setIsActive(request.isActive() == null || request.isActive());
        item.setDisplayOrder(displayOrder.positionForNewItem(category.getId(), request.displayOrder()));

        menuItemRepository.save(item);

        // Flush before the links are created. The position calculation above ran
        // bulk shift UPDATEs, and those do not flush pending changes themselves -
        // so the new row has to reach the table before they can renumber around it.
        menuItemRepository.flush();

        modifierLinks.sync(item, request.modifierGroups());

        return MenuItemMapper.toResponse(item);
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /** One item, with its modifier groups, or 404. */
    public MenuItemResponse getById(Long id) {
        MenuItem item = menuItemRepository.findWithModifierGroupsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found: " + id));
        return MenuItemMapper.toResponse(item);
    }

    /** Every item in the tenant, in category and display order. */
    public List<MenuItemResponse> getAll() {
        return menuItemRepository.findAllWithDetails()
                .stream()
                .map(MenuItemMapper::toResponse)
                .toList();
    }

    /** The items of one category, in display order. */
    public List<MenuItemResponse> getAllByCategory(Long categoryId) {
        return menuItemRepository.findAllWithDetailsByCategoryId(categoryId)
                .stream()
                .map(MenuItemMapper::toResponse)
                .toList();
    }

    // ── Update ────────────────────────────────────────────────────────────

    /**
     * Applies an edit to an existing item.
     *
     * <p>Read it as five steps:
     * <ol>
     *   <li>position - moved to another category, or within the current one</li>
     *   <li>content - names, descriptions, price</li>
     *   <li>image - queue the old file for deletion if the URL changed</li>
     *   <li>status - retired or restored</li>
     *   <li>modifier groups - synced with what the client sent</li>
     * </ol>
     */
    public MenuItemResponse update(Long id, MenuItemUpdateRequest request) {
        MenuItem item = requireMenuItemWithModifierGroups(id);

        reposition(item, request);
        applyContent(item, request);
        applyImage(item, request);
        applyActiveFlag(item, request);

        menuItemRepository.save(item);
        modifierLinks.sync(item, request.modifierGroups());

        return MenuItemMapper.toResponse(item);
    }

    /**
     * Handles the item's category and its position within it.
     *
     * <p>These are one step rather than two because moving to another category
     * <em>is</em> a position change, and doing it as one operation is what
     * keeps both categories dense.
     */
    private void reposition(MenuItem item, MenuItemUpdateRequest request) {
        Long currentCategoryId = item.getCategory().getId();

        if (!currentCategoryId.equals(request.categoryId())) {
            Category newCategory = requireCategory(request.categoryId());
            displayOrder.moveToCategory(item, newCategory, request.displayOrder());
        } else if (request.displayOrder() != null) {
            displayOrder.moveWithinCategory(item, request.displayOrder());
        }
    }

    /** Names, descriptions and price. */
    private void applyContent(MenuItem item, MenuItemUpdateRequest request) {
        item.setLocalizedNames(new HashMap<>(request.localizedNames()));
        item.setLocalizedDescriptions(copyOrEmpty(request.localizedDescriptions()));
        item.setBasePrice(request.basePrice());
    }

    /**
     * Points the item at its new image and schedules the old file for deletion.
     *
     * <p>Deletion is deferred until the transaction commits rather than done
     * inline, so a later failure in this same request cannot leave a committed
     * row pointing at a file that has already been unlinked. See
     * {@link FileStorageService#deleteAfterCommit} for the full reasoning.
     */
    private void applyImage(MenuItem item, MenuItemUpdateRequest request) {
        String previousImageUrl = item.getImageUrl();

        if (previousImageUrl != null && !previousImageUrl.equals(request.imageUrl())) {
            fileStorageService.deleteAfterCommit(previousImageUrl);
        }

        item.setImageUrl(request.imageUrl());
    }

    /**
     * Retirement is opt-in per request: a null means "do not change it", so an
     * edit that forgot about this flag cannot quietly retire an item.
     */
    private void applyActiveFlag(MenuItem item, MenuItemUpdateRequest request) {
        if (request.isActive() != null) {
            item.setIsActive(request.isActive());
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────

    /**
     * Removes an item, its modifier links and its image, then closes the gap in
     * the category's display order.
     */
    public void delete(Long id) {
        MenuItem item = requireMenuItem(id);

        // The file is removed only after this transaction commits, for the same
        // reason as in applyImage.
        fileStorageService.deleteAfterCommit(item.getImageUrl());

        // Links first: the item is about to be soft-deleted, and clearing its
        // children while the parent is still live is the only order in which
        // the constraint holds.
        List<MenuItemModifierGroup> links = linkRepository
                .findAllByMenuItemIdOrderByDisplayOrderAsc(id);
        links.forEach(linkRepository::delete);

        Long categoryId = item.getCategory().getId();
        int deletedPosition = item.getDisplayOrder();

        menuItemRepository.delete(item);

        // Flush before renumbering, or the shift would also decrement the row
        // we just deleted - a soft delete is still an UPDATE as far as the
        // bulk shift query is concerned.
        menuItemRepository.flush();
        displayOrder.closeGapAfterDelete(categoryId, deletedPosition);
    }

    // ── Lookups ───────────────────────────────────────────────────────────

    private MenuItem requireMenuItem(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found: " + id));
    }

    /**
     * Loads an item together with its modifier-group links.
     *
     * <p>The fetch-join query avoids an N+1 when the response mapper walks the
     * links.
     */
    private MenuItem requireMenuItemWithModifierGroups(Long id) {
        return menuItemRepository.findWithModifierGroupsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem not found: " + id));
    }

    private Category requireCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
    }

    /**
     * Copies the translations map, defaulting to an empty one.
     *
     * <p>Always a fresh map: assigning the request's map directly would let the
     * caller mutate a Hibernate-managed collection after the request returned.
     */
    private Map<String, String> copyOrEmpty(Map<String, String> values) {
        return values == null ? new HashMap<>() : new HashMap<>(values);
    }
}