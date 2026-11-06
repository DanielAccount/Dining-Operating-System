package com.qr_ordering_system.app.kitchen.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * One active order line on the kitchen display.
 *
 * <p>The response is intentionally flat. The KDS query is item-first: one
 * burger and one drink move independently, even though they share an order.
 * The repeated order fields are the ticket context needed beside each line;
 * the Angular client groups lines by {@code orderId} for display.</p>
 */
public record KitchenItemResponse(
        Long id,
        Long orderId,
        String invoiceNumber,
        String tableNumber,
        String orderType,
        String source,
        String customerNote,
        OffsetDateTime orderCreatedAt,
        Map<String, String> itemNameSnapshot,
        Integer quantity,
        String itemStatus,
        String orderStatus,
        List<KitchenModifierResponse> modifiers,
        OffsetDateTime itemCreatedAt,
        OffsetDateTime readyAt,
        OffsetDateTime servedAt
) {
}
