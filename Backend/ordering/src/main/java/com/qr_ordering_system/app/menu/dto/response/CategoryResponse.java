package com.qr_ordering_system.app.menu.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;

public record CategoryResponse(
        Long id,
        Map<String, String> localizedNames,
        Integer displayOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
