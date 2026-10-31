package com.qr_ordering_system.app.orders.dto.response;

import java.time.OffsetDateTime;

/**
 * One entry in an order's status story: what changed, when, who did it,
 * and any remark recorded at the time.
 */
public record OrderStatusHistoryResponse(
        Long id,
        String status,
        String remarks,
        OffsetDateTime createdAt,
        Long createdBy
) {
}
