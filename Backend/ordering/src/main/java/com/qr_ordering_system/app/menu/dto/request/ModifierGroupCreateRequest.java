package com.qr_ordering_system.app.menu.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.Map;

public record ModifierGroupCreateRequest(
        @NotEmpty(message = "At least one localized name is required")
        Map<@NotBlank @Size(max = 10) String, @NotBlank @Size(max = 100) String> localizedNames,

        @NotNull(message = "isRequired is required")
        Boolean isRequired,

        // Both are dereferenced by the service (the min <= max comparison), so
        // they must be present: a null here used to surface as a 500 NPE.
        @NotNull(message = "Min select is required")
        @Min(value = 0, message = "Min select cannot be negative")
        Integer minSelect,

        @NotNull(message = "Max select is required")
        @Min(value = 1, message = "Max select must be at least 1")
        Integer maxSelect,

        @Valid
        List<ModifierOptionDTO> options,

        // Optional on create (defaults to true). Lets an owner retire a group
        // without deleting it, which matters because delete() is refused (409)
        // while the group is attached to any menu item.
        Boolean isActive
) {}
