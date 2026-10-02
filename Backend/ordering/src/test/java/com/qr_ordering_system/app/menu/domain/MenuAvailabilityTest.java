package com.qr_ordering_system.app.menu.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The availability rule, pinned down.
 *
 * Three independent flags decide what a customer sees and whether they can
 * order an item. This is the single place that rule lives - the branch grid, the
 * customer menu and (in Phase 4) order validation all call it rather than
 * re-deriving it - so a change of behaviour here is a change of the product's
 * behaviour, and it should have to change a test on purpose.
 *
 * No Spring context: the class is a pure function, so the test is too.
 */
class MenuAvailabilityTest {

    @Nested
    @DisplayName("isOrderable - every level must say yes")
    class Orderable {

        @Test
        void everythingOn() {
            assertTrue(MenuAvailability.isOrderable(true, true, true));
        }

        @Test
        void retiredItemIsNotOrderable() {
            assertFalse(MenuAvailability.isOrderable(false, true, true));
        }

        @Test
        void branchNotOfferingItIsNotOrderable() {
            assertFalse(MenuAvailability.isOrderable(true, false, true));
        }

        @Test
        void soldOutIsNotOrderable() {
            assertFalse(MenuAvailability.isOrderable(true, true, false));
        }

        @Test
        void oneNegativeFlagIsEnough() {
            assertFalse(MenuAvailability.isOrderable(false, false, false));
        }

        @Test
        @DisplayName("null means 'no row / no answer yet' and defaults to permissive")
        void nullsArePermissive() {
            assertTrue(MenuAvailability.isOrderable(null, null, null));
            assertTrue(MenuAvailability.isOrderable(null, true, null));
            assertTrue(MenuAvailability.isOrderable(true, null, false) == false,
                    "an explicit false must still win over a null sibling");
        }

        @Test
        @DisplayName("the three flags are independent - no two combinations alias")
        void flagsAreIndependent() {
            // Retired and not-offered are indistinguishable to the customer, but
            // sold out is not: if isSoldOut ever started agreeing with
            // isOrderable for a hidden item, the reason for the split is gone.
            assertFalse(MenuAvailability.isSoldOut(false, true, false),
                    "a retired item must never be reported as merely sold out");
            assertFalse(MenuAvailability.isSoldOut(true, false, false),
                    "an item this branch does not offer must never be reported as sold out");
        }
    }

    @Nested
    @DisplayName("isSoldOut - visible but unavailable")
    class SoldOut {

        @Test
        void inStockIsNotSoldOut() {
            assertFalse(MenuAvailability.isSoldOut(true, true, true));
        }

        @Test
        void ranOutIsSoldOut() {
            assertTrue(MenuAvailability.isSoldOut(true, true, false));
        }

        @Test
        void retiredItemIsNotSoldOut() {
            assertFalse(MenuAvailability.isSoldOut(false, true, false));
        }

        @Test
        void notOfferedHereIsNotSoldOut() {
            assertFalse(MenuAvailability.isSoldOut(true, false, false));
        }
    }

    @Nested
    @DisplayName("isVisibleToCustomer - hidden items are omitted, not disabled")
    class Visibility {

        @Test
        void visibleWhenActiveAndOffered() {
            assertTrue(MenuAvailability.isVisibleToCustomer(true, true));
        }

        @Test
        @DisplayName("still visible when sold out - 'we have run out', not 'we stopped serving it'")
        void soldOutItemsStayVisible() {
            assertTrue(MenuAvailability.isVisibleToCustomer(true, true));
        }

        @Test
        void retiredIsHidden() {
            assertFalse(MenuAvailability.isVisibleToCustomer(false, true));
        }

        @Test
        void notOfferedAtThisBranchIsHidden() {
            assertFalse(MenuAvailability.isVisibleToCustomer(true, false));
        }

        @Test
        void missingRowMeansGlobalDefaultsSoVisible() {
            assertTrue(MenuAvailability.isVisibleToCustomer(true, null));
        }
    }

    @Nested
    @DisplayName("the three views must agree with each other")
    class Consistency {

        @Test
        void nothingIsBothVisibleAndSoldOutAndOrderable() {
            for (boolean active : new boolean[]{true, false}) {
                for (boolean available : new boolean[]{true, false}) {
                    for (boolean inStock : new boolean[]{true, false}) {
                        boolean visible = MenuAvailability.isVisibleToCustomer(active, available);
                        boolean soldOut = MenuAvailability.isSoldOut(active, available, inStock);
                        boolean orderable = MenuAvailability.isOrderable(active, available, inStock);

                        assertFalse(soldOut && orderable,
                                "an item cannot be both sold out and orderable");
                        assertFalse(!visible && orderable,
                                "a hidden item must never be orderable");
                        assertFalse(visible && !soldOut && !orderable,
                                "a visible item is either orderable or sold out, nothing else");
                    }
                }
            }
        }
    }
}
