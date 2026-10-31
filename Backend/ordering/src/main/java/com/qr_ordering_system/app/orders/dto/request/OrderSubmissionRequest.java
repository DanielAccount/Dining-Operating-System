package com.qr_ordering_system.app.orders.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A cart on its way in. Sent by the QR storefront and by the waiter screen
 * alike - the only difference between the two callers is who the service
 * records as the source, never the shape.
 *
 * <p>Amounts are deliberately absent: the server prices the cart from the
 * live menu, and the database CHECK on final_amount is the judge of the
 * math. A client that sends prices is a client that can lie about them.</p>
 */
public record OrderSubmissionRequest(

        /**
         * Client-generated (UUID) and reused across retries: a flaky phone
         * signal or a double tap must replay the same order, never create a
         * second one. Unique per tenant via {@code uk_order_tenant_idempotency};
         * the service checks it before anything else.
         */
        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 120, message = "idempotencyKey must be at most 120 characters")
        String idempotencyKey,

        @NotEmpty(message = "The order must contain at least one item")
        List<@Valid OrderCartLine> items,

        @Size(max = 500, message = "customerNote must be at most 500 characters")
        String customerNote,

        @Size(max = 64, message = "customerPhoneNumber must be at most 64 characters")
        String customerPhoneNumber
) {
}
