package com.qr_ordering_system.app.menu.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record ModifierOptionResponse (
        Long id,
        Map<String, String> localizedNames,
        BigDecimal extraPrice,
        Boolean isAvailable
)
{ }
