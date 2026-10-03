package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.menu.domain.Category;
import com.qr_ordering_system.app.menu.domain.DisplayOrder;
import com.qr_ordering_system.app.menu.domain.MenuItem;
import com.qr_ordering_system.app.menu.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code menu_items.display_order} a dense 1..n sequence inside a category.
 *
 * <p>This class exists so that {@link MenuItemService} reads as a list of
 * business steps ("save the fields", "put it in the right position") instead of
 * being buried under the renumbering arithmetic. Nothing here touches business
 * rules - it only answers "which number should this row get, and which
 * neighbours must move out of the way".
 *
 * <h2>The idea</h2>
 * Inside one category the positions are dense - {@code 1, 2, 3, ... n}, no gaps
 * and no duplicates. Rather than renumbering the whole category every time, we
 * shift <em>only the rows between the old position and the new one</em>, then
 * write the moved row. Moving an item from slot 1 to slot 3 is two rows moving
 * by one, not a rewrite of the category.
 *
 * <p>{@link DisplayOrder} is where the rule itself is written down - read that
 * first if the ranges below look arbitrary.
 *
 * <h2>The four situations</h2>
 * <pre>
 *   brand-new item      positionForNewItem(...)
 *   move, same category moveWithinCategory(...)
 *   move, other categ.  moveToCategory(...)
 *   delete              closeGapAfterDelete(...)
 * </pre>
 *
 * <p>{@code CategoryDisplayOrderManager} runs this identical algorithm over
 * categories. They are two separate classes rather than one generic one on
 * purpose: each is short enough to read top to bottom in one go, which matters
 * more here than saving the duplication.
 */
@Component
@RequiredArgsConstructor
public class MenuItemDisplayOrderManager {

    /**
     * A temporary parking spot used while an item is being moved between
     * categories. Zero is outside the valid 1..n range, which is the whole
     * point: while the item sits here, the bulk shifts cannot touch it.
     */
    private static final int OUT_OF_RANGE_PARKING_POSITION = DisplayOrder.PARKING_POSITION;

    /** Passed as "no item" to the shift queries, since their id is nullable. */
    private static final Long NO_ITEM = -1L;

    private final MenuItemRepository menuItemRepository;

    /**
     * Where should a brand-new item go?
     *
     * @param requestedPosition the position the caller asked for, or null to
     *                          mean "append at the end"
     * @return the position to write to the new row
     */
    public int positionForNewItem(Long categoryId, Integer requestedPosition) {
        int lastUsedPosition = menuItemRepository.findMaxDisplayOrder(categoryId);

        // Append at the end, or make room at the clamped position.
        int target = DisplayOrder.forNewRow(lastUsedPosition, requestedPosition);

        // Make room by pushing everything from the target position onward up
        // by one. The new row has not been inserted yet, so "target" itself is
        // free and no item id has to be excluded.
        if (requestedPosition != null) {
            menuItemRepository.shiftOrdersUp(categoryId, target, Integer.MAX_VALUE, NO_ITEM);
        }

        return target;
    }

    /**
     * Moves an item to a new position <em>inside the category it already
     * belongs to</em>. A null position means "leave it where it is".
     */
    public void moveWithinCategory(MenuItem item, Integer requestedPosition) {
        int oldPosition = item.getDisplayOrder();
        if (requestedPosition.equals(oldPosition)) {
            return; // already there - nothing to shift
        }

        Long categoryId = item.getCategory().getId();
        int lastUsedPosition = menuItemRepository.findMaxDisplayOrder(categoryId);

        // No +1 on the upper bound here: the item is already counted in
        // lastUsedPosition, so it has no free slot to append into.
        int target = DisplayOrder.forExistingRow(lastUsedPosition, requestedPosition);

        if (target < oldPosition) {
            // Moving UP (e.g. 3 -> 1): rows in [target, old) step up by one.
            menuItemRepository.shiftOrdersUp(categoryId, target, oldPosition, item.getId());
        } else if (target > oldPosition) {
            // Moving DOWN (e.g. 1 -> 3): rows in (old, target] step down by one.
            menuItemRepository.shiftOrdersDown(categoryId, oldPosition, target, item.getId());
        }

        item.setDisplayOrder(target);
    }

    /**
     * Moves an item into a different category: compact the category it leaves,
     * then place it in the one it joins.
     *
     * <p>The item is parked at position 0 and flushed first. That step matters
     * and is easy to remove by accident: {@code @Modifying} queries do not
     * flush pending changes, so without the flush the bulk UPDATEs would still
     * see the item sitting in its <em>old</em> category and renumber a row that
     * is on its way out.
     */
    public void moveToCategory(MenuItem item, Category newCategory, Integer requestedPosition) {
        Long oldCategoryId = item.getCategory().getId();
        int oldPosition = item.getDisplayOrder();
        Long newCategoryId = newCategory.getId();

        item.setCategory(newCategory);
        item.setDisplayOrder(OUT_OF_RANGE_PARKING_POSITION);
        menuItemRepository.saveAndFlush(item);

        // The item has genuinely left the old category in the database now,
        // so closing that category's gap is safe.
        menuItemRepository.shiftOrdersAfterDelete(oldCategoryId, oldPosition);

        item.setDisplayOrder(positionForNewItem(newCategoryId, requestedPosition));
    }

    /**
     * Closes the gap left by a deleted row.
     *
     * <p>The caller must have flushed the delete <em>before</em> calling this.
     * Soft delete turns the delete into an UPDATE of the deleted flag, and the
     * bulk shift does not flush first - so if the delete is still pending, the
     * shift sees the dying row and decrements its position as well.
     */
    public void closeGapAfterDelete(Long categoryId, int deletedPosition) {
        menuItemRepository.shiftOrdersAfterDelete(categoryId, deletedPosition);
    }
}