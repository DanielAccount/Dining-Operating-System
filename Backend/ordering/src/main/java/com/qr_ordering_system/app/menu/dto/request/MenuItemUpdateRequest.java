package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MenuItemUpdateRequest(

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
         * New position within the category. Null leaves the item where it is,
         * so an ordinary edit that omits it never triggers a renumber.
         *
         * Moving the item to a different category is handled by the service:
         * the old category is compacted and the item is appended to the new one.
         */
        @Min(value = 1, message = "Display order must be at least 1")
        Integer displayOrder,

        Boolean isActive,

        @Valid
        List<MenuItemModifierGroupDTO> modifierGroups
) {
}
