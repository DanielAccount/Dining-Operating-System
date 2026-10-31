package com.qr_ordering_system.app.orders.dto.response;

import java.math.BigDecimal;

/**
 * One selected modifier option on an order line - e.g. "Oat milk, +500" -
 * snapshotted at submission.
 */
public record OrderModifierResponse(
        Long id,
        String name,
        BigDecimal price
) {
}
