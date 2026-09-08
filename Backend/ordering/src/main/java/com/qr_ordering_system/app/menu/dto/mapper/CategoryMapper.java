package com.qr_ordering_system.app.menu.dto.mapper;

import com.qr_ordering_system.app.menu.domain.Category;
import com.qr_ordering_system.app.menu.dto.response.CategoryResponse;
import com.qr_ordering_system.app.menu.dto.response.CategorySummaryResponse;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getLocalizedNames(),
                category.getDisplayOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    public static CategorySummaryResponse toSummary(Category category) {
        return new CategorySummaryResponse(
                category.getId(),
                category.getLocalizedNames(),
                category.getDisplayOrder()
        );
    }

}
