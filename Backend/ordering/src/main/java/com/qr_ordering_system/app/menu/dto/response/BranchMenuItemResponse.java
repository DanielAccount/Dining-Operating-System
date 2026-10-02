package com.qr_ordering_system.app.menu.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One row of the branch pricing grid.
 *
 * The list endpoint returns ONE row PER MENU ITEM in the tenant,
 * merged with any branch override. Rows without an override have
 * branchMenuItemId == null, priceOverride == null, isAvailable == true and
 * isInStock == true (global defaults).
 *
 * The three availability levels, each answering a different question:
 *   item.isActive        -> is the dish on the menu at all?      (tenant-wide)
 *   isAvailable          -> does THIS branch offer it?           (per branch)
 *   isInStock            -> does THIS branch have it right now?  (per branch)
 */
public record BranchMenuItemResponse(
        Long branchMenuItemId,          // null when no branch row exists yet
        Long menuItemId,
        Map<String, String> localizedNames,
        CategorySummaryResponse category,
        BigDecimal basePrice,           // global price
        BigDecimal priceOverride,       // null = follow base price
        BigDecimal effectivePrice,      // priceOverride != null ? priceOverride : basePrice
        Boolean itemIsActive,           // false = retired tenant-wide
        Boolean isAvailable,            // this branch offers it
        Boolean isInStock,              // this branch has it today
        /** Single resolved answer: can a customer order this at this branch? */
        Boolean isOrderable
) {}
