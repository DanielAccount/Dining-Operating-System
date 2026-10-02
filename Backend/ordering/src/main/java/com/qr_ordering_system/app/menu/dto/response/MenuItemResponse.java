package com.qr_ordering_system.app.menu.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record MenuItemResponse(
        Long id,
        CategorySummaryResponse category,
        Map<String, String> localizedNames,
        Map<String, String> localizedDescriptions,
        BigDecimal basePrice,
        String imageUrl,
        /** Position within the owning category, 1-based. */
        Integer displayOrder,
        Boolean isActive,
        List<MenuItemModifierGroupResponse> modifierGroups,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
