package com.qr_ordering_system.app.orders.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One cart line: which dish, how many, and which modifier options are
 * selected for it (e.g. "Oat milk" on one of the two lattes).
 */
public record OrderCartLine(

        @NotNull(message = "menuItemId is required")
        Long menuItemId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 99, message = "quantity cannot exceed 99 per line")
        Integer quantity,

        List<@Valid OrderCartModifier> modifiers
) {
}
