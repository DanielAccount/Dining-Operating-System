package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MenuItemCreateRequest(

        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotEmpty(message = "At least one localized name is required")
        Map<@NotBlank @Size(max = 10) String, @NotBlank @Size(max = 150) String> localizedNames,

        Map<@NotBlank @Size(max = 10) String, @Size(max = 500) String> localizedDescriptions,

        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.00", message = "Base price cannot be negative")
        BigDecimal basePrice,

        @Size(max = 500, message = "Image URL cannot exceed 500 characters")
        String imageUrl,

        /**
         * Position within the category. Omit or null to append at the end.
         * Values below 1 are clamped to 1; values past the end are clamped to
         * the last position, so the sequence can never develop a hole.
         */
        @Min(value = 1, message = "Display order must be at least 1")
        Integer displayOrder,

        Boolean isActive,

        @Valid
        List<MenuItemModifierGroupDTO> modifierGroups
) {
}
