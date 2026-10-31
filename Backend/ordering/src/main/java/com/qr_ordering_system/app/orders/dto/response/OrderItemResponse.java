package com.qr_ordering_system.app.orders.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One order line with its submission-time snapshot: what the customer
 * actually ordered, at the price they were shown, immune to later menu
 * edits.
 */
public record OrderItemResponse(
        Long id,
        Map<String, String> itemNameSnapshot,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String itemStatus,
        List<OrderModifierResponse> modifiers
) {
}
