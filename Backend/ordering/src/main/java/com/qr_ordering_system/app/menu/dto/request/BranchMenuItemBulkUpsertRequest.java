package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Bulk upsert for the branch pricing grid — the UI edits many rows
 * and saves them in one request.
 */
public record BranchMenuItemBulkUpsertRequest(

        @NotEmpty(message = "At least one item is required")
        List<@Valid Item> items
) {
    public record Item(

            @NotNull(message = "Menu item id is required")
            Long menuItemId,

            @DecimalMin(value = "0.00", message = "Price override cannot be negative")
            BigDecimal priceOverride,

            @NotNull(message = "Availability is required")
            Boolean isAvailable,

            /** Null keeps the current value. */
            Boolean isInStock
    ) {}
}
