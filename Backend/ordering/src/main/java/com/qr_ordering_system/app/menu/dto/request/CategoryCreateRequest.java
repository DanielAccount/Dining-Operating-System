package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CategoryCreateRequest(

        // { "en": "Burgers", "mm": "ဘာဂါ" } at least one language must be present.
        // Every key must be a short locale code and every value a non-blank name.
        @NotEmpty(message = "At least one localized name is required")
        Map<@NotBlank @Size(max = 10) String, @NotBlank @Size(max = 100) String> localizedNames,

        // Display order is optional on create; service will default to "append at end"
        @Positive(message = "Display order must be 1 or greater")
        Integer displayOrder
) {
}
