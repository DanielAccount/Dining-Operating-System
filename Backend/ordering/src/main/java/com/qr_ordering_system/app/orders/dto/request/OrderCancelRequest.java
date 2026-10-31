package com.qr_ordering_system.app.orders.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Why an order was cancelled - the audit trail cares, the customer rarely
 * needs to see more than "cancelled".
 */
public record OrderCancelRequest(
        @Size(max = 255, message = "cancel reason must be at most 255 characters")
        String reason
) {
}
