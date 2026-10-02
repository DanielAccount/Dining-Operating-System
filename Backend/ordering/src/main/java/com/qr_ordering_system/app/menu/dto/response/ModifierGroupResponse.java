package com.qr_ordering_system.app.menu.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ModifierGroupResponse(
        Long id,
        Map<String, String> localizedNames,
        Boolean isRequired,
        Integer minSelect,
        Integer maxSelect,
        Boolean isActive,
        List<ModifierOptionResponse> options,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
