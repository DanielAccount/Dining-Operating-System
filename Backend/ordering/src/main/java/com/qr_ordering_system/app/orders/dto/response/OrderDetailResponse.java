package com.qr_ordering_system.app.orders.dto.response;

import java.util.List;

/**
 * An order plus its full status timeline - the staff detail view.
 * (The customer's "your orders" list deliberately shows only the current
 * status; the story is for the people running the restaurant.)
 */
public record OrderDetailResponse(
        OrderResponse order,
        List<OrderStatusHistoryResponse> history
) {
}
