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

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        Category category = new Category();
        category.setLocalizedNames(new HashMap<>(request.localizedNames()));

        int targetOrder = request.displayOrder() != null
                ? Math.max(1, request.displayOrder())
                : getNextDisplayOrder();

        int maxOrder = getNextDisplayOrder();
        if (targetOrder > maxOrder) {
            targetOrder = maxOrder;
        }

        // Shift existing orders to make space
        categoryRepository.shiftOrdersUp(targetOrder, Integer.MAX_VALUE, -1L);

        category.setDisplayOrder(targetOrder);
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

    @Transactional
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

    @Transactional
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

        // Compact remaining display orders
        categoryRepository.shiftOrdersAfterDelete(deletedOrder);
    }

    private int getNextDisplayOrder() {
        return categoryRepository.findMaxDisplayOrder() + 1;
    }
}