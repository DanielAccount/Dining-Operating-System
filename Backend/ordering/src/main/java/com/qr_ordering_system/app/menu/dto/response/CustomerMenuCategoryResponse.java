package com.qr_ordering_system.app.menu.dto.response;

import java.util.List;
import java.util.Map;

/**
 * A category on the customer QR menu, with the items visible at this branch.
 *
 * Deliberately NOT a reuse of CategoryResponse: the management DTO exposes
 * internal state (timestamps, display order) a customer has no business seeing.
 */
public record CustomerMenuCategoryResponse(
        Long id,
        Map<String, String> localizedNames,
        List<CustomerMenuItemResponse> items
) {}
