package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

public record ModifierOptionDTO (
        Long id,

        @NotEmpty(message = "At least one localized name is required")
        Map<@NotBlank @Size(max = 10) String, @NotBlank @Size(max = 100) String> localizedNames,

        @NotNull(message = "Extra price is required")
        @DecimalMin(value = "0.00", message = "Extra price must be 0 or greater")
        BigDecimal extraPrice,

        Boolean isAvailable
) {
}
