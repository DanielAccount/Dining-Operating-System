package com.qr_ordering_system.app.tables.dto.response;

import com.qr_ordering_system.app.menu.dto.response.CustomerMenuCategoryResponse;

import java.util.List;

/**
 * Everything a customer's phone needs to render the storefront, in one
 * round trip: whose menu (business, branch), which seat (table number),
 * and the menu itself, availability rules already applied.
 *
 * Contains no internal ids beyond what ordering will need (menu item /
 * modifier ids), no tenant id, no branch id - a public response exposes
 * the minimum surface a bad actor could study.
 */
public record PublicTableMenuResponse(
        String businessName,
        String address,
        String branchName,
        String tableNumber,
        List<CustomerMenuCategoryResponse> categories
) {
}
