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

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MenuItemRepository menuItemRepository;

    public CategoryResponse create(CategoryCreateRequest request) {
        Category category = new Category();
        category.setLocalizedNames(new HashMap<>(request.localizedNames()));

        int maxOrder = categoryRepository.findMaxDisplayOrder();

        if (request.displayOrder() != null) {
            // Insert at a specific position; clamp to [1, max+1]
            int targetOrder = Math.max(1, Math.min(request.displayOrder(), maxOrder + 1));
            // Shift existing items at or after targetOrder up by 1
            categoryRepository.shiftOrdersUp(targetOrder, Integer.MAX_VALUE, -1L);
            category.setDisplayOrder(targetOrder);
        } else {
            // No position requested — append at end. maxOrder is 0 for an empty
            // tenant, so the very first category lands on position 1 and the
            // 1-based dense sequence holds from the start.
            category.setDisplayOrder(maxOrder + 1);
        }

        return CategoryMapper.toResponse(categoryRepository.save(category));
    }

    public CategoryResponse getById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
        return CategoryMapper.toResponse(category);
    }

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(CategoryMapper::toResponse)
                .toList();
    }

    // NOTE: display-order reordering is not fully atomic under concurrent requests.
    // The @Version optimistic lock protects the entity itself, but bulk @Modifying
    // UPDATE queries bypass version checks. Acceptable for MVP menu management.
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        category.setLocalizedNames(new HashMap<>(request.localizedNames()));

        if (request.displayOrder() != null && !request.displayOrder().equals(category.getDisplayOrder())) {
            int oldOrder = category.getDisplayOrder();
            int newOrder = Math.max(1, request.displayOrder());
            int maxOrder = categoryRepository.findMaxDisplayOrder();

            if (newOrder > maxOrder) {
                newOrder = maxOrder;
            }

            if (newOrder < oldOrder) {
                // Moving UP (e.g., 3 -> 1): Shift items from newOrder to oldOrder-1 UP by +1
                categoryRepository.shiftOrdersUp(newOrder, oldOrder, id);
            } else if (newOrder > oldOrder) {
                // Moving DOWN (e.g., 1 -> 3): Shift items from oldOrder+1 to newOrder DOWN by -1
                categoryRepository.shiftOrdersDown(oldOrder, newOrder, id);
            }

            category.setDisplayOrder(newOrder);
        }

        return CategoryMapper.toResponse(categoryRepository.save(category));
    }

    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));

        if (menuItemRepository.countByCategory(category) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete category " + id + " because it still contains menu items");
        }

        int deletedOrder = category.getDisplayOrder();
        categoryRepository.delete(category);
        // Flush before renumbering, exactly as MenuItemService.delete does.
        //
        // @SoftDelete turns delete() into an UPDATE of is_deleted, and bulk
        // @Modifying queries do not flush pending changes first. Without this the
        // soft-delete has not been issued yet, so shiftOrdersAfterDelete would
        // still see the deleted row and decrement its position too.
        categoryRepository.flush();
        categoryRepository.shiftOrdersAfterDelete(deletedOrder);
    }

}