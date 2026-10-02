package com.qr_ordering_system.app.menu.dto.response;

import java.util.Map;

public record MenuItemModifierGroupResponse(
        Long id,
        Long modifierGroupId,
        Map<String, String> modifierGroupLocalizedNames,
        Integer displayOrder,
        Boolean requiredOverride,
        Boolean isActive
) {
}
