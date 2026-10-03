package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.common.exceptions.ResourceNotFoundException;
import com.qr_ordering_system.app.menu.domain.Category;
import com.qr_ordering_system.app.menu.dto.mapper.CategoryMapper;
import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.CategoryUpdateRequest;
import com.qr_ordering_system.app.menu.dto.response.CategoryResponse;
import com.qr_ordering_system.app.menu.repository.CategoryRepository;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;

/**
 * Create, read, update and delete for menu categories.
 *
 * <p>Like {@link MenuItemService}, this class only orchestrates; the display
 * order arithmetic lives in {@link CategoryDisplayOrderManager} so the two stay
 * readable side by side.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final CategoryDisplayOrderManager displayOrder;

    /**
     * Adds a category at the end of, or at a chosen position in, the tenant's
     * category list.
     */
    public CategoryResponse create(CategoryCreateRequest request) {
        Category category = new Category();
        category.setLocalizedNames(new HashMap<>(request.localizedNames()));
        category.setDisplayOrder(displayOrder.positionForNewCategory(request.displayOrder()));

        return CategoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse getById(Long id) {
        Category category = requireCategory(id);
        return CategoryMapper.toResponse(category);
    }

    /** Every category in the tenant, in display order. */
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }

    /**
     * Applies an edit to an existing category: its name, and optionally a new
     * position in the list.
     */
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = requireCategory(id);

        category.setLocalizedNames(new HashMap<>(request.localizedNames()));

        if (request.displayOrder() != null) {
            displayOrder.moveCategory(category, request.displayOrder());
        }

        return CategoryMapper.toResponse(categoryRepository.save(category));
    }

    /**
     * Deletes an empty category and closes the gap it leaves.
     *
     * <p>A category that still holds menu items is refused: deleting it would
     * either orphan those items or silently delete them with it, and neither is
     * something a single "Delete" click should do.
     */
    public void delete(Long id) {
        Category category = requireCategory(id);

        if (menuItemRepository.countByCategory(category) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete category " + id + " because it still contains menu items");
        }

        int deletedPosition = category.getDisplayOrder();

        categoryRepository.delete(category);

        // Flush before renumbering. A soft delete is an UPDATE of the deleted
        // flag and the bulk shift does not flush first, so without this the
        // shift would still see the dying row and decrement its position too.
        categoryRepository.flush();
        displayOrder.closeGapAfterDelete(deletedPosition);
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }
}