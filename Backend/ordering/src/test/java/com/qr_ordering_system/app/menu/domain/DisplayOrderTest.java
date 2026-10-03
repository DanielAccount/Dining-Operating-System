package com.qr_ordering_system.app.menu.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The display-order rule, tested on its own.
 *
 * <p>{@link DisplayOrder} is pure arithmetic with no Spring wiring, so it can
 * be checked exhaustively here instead of only through the slower integration
 * tests. The cases that matter are the boundaries - off-by-one errors at
 * position 1 and at the end of the sequence are exactly what produces a broken
 * menu ordering in production.
 */
class DisplayOrderTest {

    @Nested
    @DisplayName("forNewRow - the row is not in the sequence yet")
    class ForNewRow {

        @Test
        @DisplayName("null means append at the end")
        void nullAppends() {
            // 4 rows exist, so the next free slot is 5.
            assertEquals(5, DisplayOrder.forNewRow(4, null));
        }

        @Test
        @DisplayName("an empty sequence appends at position 1, not 0")
        void emptySequenceStartsAtOne() {
            assertEquals(1, DisplayOrder.forNewRow(0, null));
        }

        @Test
        @DisplayName("a new row MAY use lastUsed + 1")
        void allowsOneBeyondTheEnd() {
            // This is the difference from forExistingRow: the row is not
            // counted in lastUsedPosition yet, so the last slot is free.
            assertEquals(5, DisplayOrder.forNewRow(4, 5));
        }

        @Test
        @DisplayName("a position past the end clamps down instead of leaving a hole")
        void clampsAboveTheEnd() {
            assertEquals(5, DisplayOrder.forNewRow(4, 999));
        }

        @Test
        @DisplayName("a position below the start clamps up to 1")
        void clampsBelowTheStart() {
            assertEquals(1, DisplayOrder.forNewRow(4, 0));
            assertEquals(1, DisplayOrder.forNewRow(4, -7));
        }

        @Test
        @DisplayName("an in-range position is used unchanged")
        void keepsRequestedPosition() {
            assertEquals(1, DisplayOrder.forNewRow(4, 1));
            assertEquals(3, DisplayOrder.forNewRow(4, 3));
        }
    }

    @Nested
    @DisplayName("forExistingRow - the row is already in the sequence")
    class ForExistingRow {

        @Test
        @DisplayName("may NOT use lastUsed + 1")
        void doesNotAllowOneBeyondTheEnd() {
            // The row being moved is already counted in lastUsedPosition, so
            // asking for 5 here would step past its neighbours and leave a gap.
            assertEquals(4, DisplayOrder.forExistingRow(4, 5));
        }

        @Test
        @DisplayName("clamps to the end rather than the end plus one")
        void clampsAboveTheEnd() {
            assertEquals(4, DisplayOrder.forExistingRow(4, 999));
        }

        @Test
        @DisplayName("clamps up to position 1")
        void clampsBelowTheStart() {
            assertEquals(1, DisplayOrder.forExistingRow(4, 0));
            assertEquals(1, DisplayOrder.forExistingRow(4, -7));
        }

        @Test
        @DisplayName("an in-range position is used unchanged")
        void keepsRequestedPosition() {
            assertEquals(1, DisplayOrder.forExistingRow(4, 1));
            assertEquals(4, DisplayOrder.forExistingRow(4, 4));
        }
    }

    @Test
    @DisplayName("the parking position is outside every legal range")
    void parkingPositionIsOutsideTheLegalRange() {
        // The parking spot must never be a resting value, or the sequence would
        // start at 0 and the shift UPDATEs could match a row mid-move.
        assertEquals(0, DisplayOrder.PARKING_POSITION);
        assertEquals(1, DisplayOrder.FIRST_POSITION);
        assertEquals(1, DisplayOrder.forNewRow(3, DisplayOrder.PARKING_POSITION));
        assertEquals(1, DisplayOrder.forExistingRow(3, DisplayOrder.PARKING_POSITION));
    }
}