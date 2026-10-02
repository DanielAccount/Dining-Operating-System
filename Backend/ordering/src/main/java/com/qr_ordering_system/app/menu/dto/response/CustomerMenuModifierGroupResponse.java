package com.qr_ordering_system.app.menu.dto.response;

import java.util.List;
import java.util.Map;

/**
 * A modifier group a customer may pick from.
 *
 * Inactive links, inactive groups and unavailable options are filtered out
 * upstream, and a group left with no selectable options is dropped entirely -
 * there is nothing a customer could do with it.
 */
public record CustomerMenuModifierGroupResponse(
        Long id,
        Map<String, String> localizedNames,
        boolean required,
        int minSelect,
        int maxSelect,
        List<CustomerMenuModifierOptionResponse> options
) {}
