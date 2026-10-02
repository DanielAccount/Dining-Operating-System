package com.qr_ordering_system.app.menu.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/** A selectable modifier option. Only available options reach the customer. */
public record CustomerMenuModifierOptionResponse(
        Long id,
        Map<String, String> localizedNames,
        BigDecimal extraPrice
) {}
