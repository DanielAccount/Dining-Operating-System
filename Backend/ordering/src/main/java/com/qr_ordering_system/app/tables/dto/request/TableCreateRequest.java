package com.qr_ordering_system.app.tables.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates a table in a branch. One field: the human label ("T5", "Patio 2")
 * - the QR slug is minted server-side and is never accepted from a client.
 */
public record TableCreateRequest(

        @NotBlank(message = "Table number is required")
        @Size(max = 64, message = "Table number must be at most 64 characters")
        String tableNumber
) {
}
