package com.qr_ordering_system.app.orders.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * An order as caller and customer both see it after submission.
 *
 * <p>{@code sessionCode} is the phone's handle for the table: the
 * storefront stores it (localStorage) and re-attaches with it on the next
 * scan of the same table, so "your orders" shows the table's orders.</p>
 *
 * <p>Items arrive with their submission-time snapshots - name, unit price,
 * line total - not with whatever the menu says now.</p>
 */
public record OrderResponse(
        Long id,
        String status,
        String orderType,
        String source,
        String tableNumber,
        String sessionCode,
        String customerNote,
        String customerPhoneNumber,
        String invoiceNumber,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal discountAmount,
        BigDecimal serviceCharge,
        BigDecimal finalAmount,
        OffsetDateTime createdAt,
        List<OrderItemResponse> items
) {
}
