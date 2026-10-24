package com.qr_ordering_system.app.tables.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Renames a table. The number is the only editable field: the QR slug is
 * managed separately (regenerate-qr endpoint) so renaming a table never
 * silently kills its printed codes.
 */
public record TableUpdateRequest(

        @NotBlank(message = "Table number is required")
        @Size(max = 64, message = "Table number must be at most 64 characters")
        String tableNumber
) {
}
