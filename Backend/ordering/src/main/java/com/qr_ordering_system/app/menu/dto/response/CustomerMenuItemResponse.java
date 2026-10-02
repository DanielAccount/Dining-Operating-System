package com.qr_ordering_system.app.menu.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One item as a customer sees it at a specific branch.
 *
 * The availability decision is already made server-side, via
 * {@link com.qr_ordering_system.app.menu.domain.MenuAvailability}. A client
 * should render {@code soldOut} and refuse to add the item to the cart; it must
 * not re-derive the rule from is_active / is_available / is_in_stock, because
 * that logic is exactly what drifted out of sync before.
 *
 * Note there is no isOrderable field: a visible item is either orderable or
 * soldOut, and those are the only two states that reach a customer. Items this
 * branch does not offer, and retired items, are omitted entirely.
 */
public record CustomerMenuItemResponse(
        Long id,
        Map<String, String> localizedNames,
        Map<String, String> localizedDescriptions,
        String imageUrl,
        /** Effective price for this branch: branch override, else the base price. */
        BigDecimal price,
        /** Visible but not orderable - this branch has run out. */
        boolean soldOut,
        List<CustomerMenuModifierGroupResponse> modifierGroups
) {}
