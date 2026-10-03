package com.qr_ordering_system.app.menu.domain;

/**
 * The single written-down definition of how {@code display_order} works.
 *
 * <p>Positions are a <strong>dense 1..n sequence</strong>: inside one parent
 * (a tenant's categories, or a category's menu items) every position from 1 to n
 * is used exactly once, with no gaps and no duplicates.
 *
 * <pre>
 *   n = 4      1  2  3  4
 *   delete 2   1  _  3  4   ->  1  2  3      the gap is closed, positions stay dense
 *   insert @1  _  1  2  3  4 ->  1  2  3  4   the others step down, the new row takes 1
 * </pre>
 *
 * <h2>Two ranges, and the difference matters</h2>
 * A new row and an existing row have <em>different</em> legal positions, and
 * conflating them is the classic bug in this algorithm:
 * <ul>
 *   <li>{@link #forNewRow} may use {@code lastUsed + 1}, because the row is not
 *       in the sequence yet, so appending leaves the last slot free.</li>
 *   <li>{@link #forExistingRow} may only use {@code lastUsed}, because the row
 *       being moved is already counted in that number.</li>
 * </ul>
 *
 * <p>Anything a caller sends outside the legal range is clamped rather than
 * rejected, so a UI that is one step out of date can never punch a hole in the
 * sequence.
 *
 * <h2>Position 0 is reserved</h2>
 * {@link #PARKING_POSITION} is deliberately outside the legal range. It is used
 * as a temporary holding spot while a row is being moved between parents: while
 * a row sits at 0, the bulk shift UPDATEs cannot match it, so they cannot
 * renumber a row that is on its way out. See
 * {@link com.qr_ordering_system.app.menu.service.MenuItemDisplayOrderManager}.
 *
 * <h2>Concurrency</h2>
 * Reordering is done with bulk {@code @Modifying} UPDATEs, which bypass the
 * {@code @Version} optimistic lock. Two owners reordering the same list at the
 * same instant can therefore interleave and lose an update. This is a known,
 * accepted trade-off for MVP menu management - it is only worth solving if
 * reordering ever becomes a hot path.
 *
 * <p>Pure and Spring-free on purpose, like {@link MenuAvailability}: the rule
 * must be readable and testable without standing up an application context.
 */
public final class DisplayOrder {

    /** The first legal position in any sequence. */
    public static final int FIRST_POSITION = 1;

    /**
     * Outside the legal range on purpose - see the class comment. Never
     * persisted as a resting value; a row must always end up at 1 or higher.
     */
    public static final int PARKING_POSITION = 0;

    private DisplayOrder() {
    }

    /**
     * Where a row that is not in the sequence yet should go.
     *
     * @param lastUsedPosition highest position currently taken, or 0 if empty
     * @param requestedPosition what the caller asked for, or null for
     *                          "append at the end"
     * @return a position in {@code [1, lastUsedPosition + 1]}
     */
    public static int forNewRow(int lastUsedPosition, Integer requestedPosition) {
        if (requestedPosition == null) {
            return lastUsedPosition + 1;
        }
        return clamp(requestedPosition, FIRST_POSITION, lastUsedPosition + 1);
    }

    /**
     * Where a row that is already in the sequence should go.
     *
     * <p>Note the upper bound is {@code lastUsedPosition}, not {@code +1}: the
     * row being moved is itself counted, so there is no free slot at the end.
     * If it did allow {@code +1} the row would step past its own neighbours and
     * leave a gap behind.
     *
     * @param requestedPosition what the caller asked for
     * @return a position in {@code [1, lastUsedPosition]}
     */
    public static int forExistingRow(int lastUsedPosition, int requestedPosition) {
        return clamp(requestedPosition, FIRST_POSITION, lastUsedPosition);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}