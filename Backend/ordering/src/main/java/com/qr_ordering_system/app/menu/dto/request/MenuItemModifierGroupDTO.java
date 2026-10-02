package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MenuItemModifierGroupDTO(

        @NotNull(message = "Modifier group ID is required")
        Long modifierGroupId,

        @Min(value = 0, message = "Display order cannot be negative")
        Integer displayOrder,

        Boolean requiredOverride,

        Boolean isActive
) {
}
