package com.qr_ordering_system.app.menu.domain;

/**
 * The single definition of "can a customer order this?".
 *
 * This rule was previously undefined: the customer-menu query filtered
 * {@code is_active} and ignored stock entirely, while the UI carried two
 * different toggles that both meant "can't be ordered". Three levels, three
 * different questions, and one combined answer:
 *
 * <pre>
 *   menu_items.is_active           tenant-wide   is the dish on the menu at all?
 *   branch_menu_items.is_available per branch    does THIS branch offer it?
 *   branch_menu_items.is_in_stock  per branch    does THIS branch have it today?
 * </pre>
 *
 * A null at any level is treated as the permissive default, matching how a
 * missing branch_menu_items row resolves (follow the global defaults).
 *
 * Deliberately a pure function with no Spring wiring: the rule must be
 * callable from the branch grid, the customer menu and - later - order
 * validation without any of them re-deriving it.
 */
public final class MenuAvailability {

    private MenuAvailability() {
    }

    /** True only when every level says yes. */
    public static boolean isOrderable(Boolean itemActive, Boolean branchAvailable, Boolean branchInStock) {
        return isTrue(itemActive) && isTrue(branchAvailable) && isTrue(branchInStock);
    }

    /**
     * Visible on the customer menu but not orderable, because the branch has
     * run out. A retired item or one the branch does not offer is hidden
     * entirely instead, so it is never "sold out" from a customer's view.
     */
    public static boolean isSoldOut(Boolean itemActive, Boolean branchAvailable, Boolean branchInStock) {
        return isTrue(itemActive) && isTrue(branchAvailable) && !isTrue(branchInStock);
    }

    /**
     * Should the item appear on the customer menu at all? A retired dish, or
     * one this branch does not offer, is omitted rather than shown disabled -
     * "sold out" should mean "we have run out", never "we stopped serving it".
     */
    public static boolean isVisibleToCustomer(Boolean itemActive, Boolean branchAvailable) {
        return isTrue(itemActive) && isTrue(branchAvailable);
    }

    private static boolean isTrue(Boolean value) {
        return value == null || value;
    }
}
