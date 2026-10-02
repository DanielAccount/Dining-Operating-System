package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Upsert payload for one branch-menu-item row.
 * priceOverride = null means "use the global base price".
 */
public record BranchMenuItemUpsertRequest(

        @DecimalMin(value = "0.00", message = "Price override cannot be negative")
        BigDecimal priceOverride,

        @NotNull(message = "Availability is required")
        Boolean isAvailable,

        /** Null keeps the current value. */
        Boolean isInStock
) {}
