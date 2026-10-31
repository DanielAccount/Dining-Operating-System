package com.qr_ordering_system.app.orders.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * One selected modifier option. Only the option id travels - the name and
 * price are snapshotted server-side from the live menu, same as item names.
 */
public record OrderCartModifier(

        @NotNull(message = "modifierOptionId is required")
        Long modifierOptionId
) {
}
