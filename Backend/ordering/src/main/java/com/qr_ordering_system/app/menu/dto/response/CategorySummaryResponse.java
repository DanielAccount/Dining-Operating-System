package com.qr_ordering_system.app.menu.dto.response;

import java.util.Map;

//optional — for dropdowns where you don't need timestamps
public record CategorySummaryResponse(
        Long id,
        Map<String, String> localizedNames,
        Integer displayOrder
) {
}
