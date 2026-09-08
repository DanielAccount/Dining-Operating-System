package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CategoryUpdateRequest(

        @NotEmpty(message = "At least one localized name is required")
        Map<@NotBlank @Size(max = 10) String, @NotBlank @Size(max = 100) String> localizedNames,

        @Positive(message = "Display order must be 1 or greater")
        Integer displayOrder
) {
}
