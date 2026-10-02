package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

public record ModifierGroupUpdateRequest(
        @NotEmpty(message = "At least one localized name is required")
        Map<@NotBlank @Size(max = 10) String, @NotBlank @Size(max = 100) String> localizedNames,

        @NotNull(message = "isRequired is required")
        Boolean isRequired,

        @NotNull(message = "Min select is required")
        @Min(value = 0, message = "Min select cannot be negative")
        Integer minSelect,

        @NotNull(message = "Max select is required")
        @Min(value = 1, message = "Max select must be at least 1")
        Integer maxSelect,

        @Valid
        List<ModifierOptionDTO> options,

        /** null leaves the current value untouched. */
        Boolean isActive
) {
}
