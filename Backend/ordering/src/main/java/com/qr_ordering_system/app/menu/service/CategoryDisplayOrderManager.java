package com.qr_ordering_system.app.menu.service;

import com.qr_ordering_system.app.menu.domain.Category;
import com.qr_ordering_system.app.menu.domain.DisplayOrder;
import com.qr_ordering_system.app.menu.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code categories.display_order} a dense 1..n sequence for the tenant.
 *
 * <p>This is the category-level twin of
 * {@link MenuItemDisplayOrderManager} - same rule, same four situations, same
 * shift strategy. The rule itself lives in {@link DisplayOrder}; read that
 * class first.
 *
 * <p>Where the two differ is the scope. Categories are ordered once per tenant,
 * so there is no "move to another parent" case here - a category is simply
 * created, moved within the tenant, or deleted.
 *
 * <p>Kept as a separate class rather than made generic on purpose: each is short
 * enough to read top to bottom in one pass, and that is worth more for a
 * reviewer than the removed duplication.
 */
@Component
@RequiredArgsConstructor
public class CategoryDisplayOrderManager {

    /** Passed as "no category" to the shift queries, since their id is nullable. */
    private static final Long NO_CATEGORY = -1L;

    private final CategoryRepository categoryRepository;

    /**
     * Where should a brand-new category go?
     *
     * @param requestedPosition the position the caller asked for, or null to
     *                          mean "append at the end"
     */
    public int positionForNewCategory(Integer requestedPosition) {
        int lastUsedPosition = categoryRepository.findMaxDisplayOrder();

        // Append at the end, or make room at the clamped position.
        int target = DisplayOrder.forNewRow(lastUsedPosition, requestedPosition);

        // Make room by pushing everything from the target position onward up by
        // one. The new row does not exist yet, so "target" is free and no
        // category id has to be excluded.
        if (requestedPosition != null) {
            categoryRepository.shiftOrdersUp(target, Integer.MAX_VALUE, NO_CATEGORY);
        }

        return target;
    }

    /**
     * Moves a category to a new position among its siblings. A null position
     * means "leave it where it is".
     */
    public void moveCategory(Category category, Integer requestedPosition) {
        int oldPosition = category.getDisplayOrder();
        if (requestedPosition.equals(oldPosition)) {
            return; // already there - nothing to shift
        }

        int lastUsedPosition = categoryRepository.findMaxDisplayOrder();

        // No +1 on the upper bound: this category is already counted in
        // lastUsedPosition, so it has no free slot to append into.
        int target = DisplayOrder.forExistingRow(lastUsedPosition, requestedPosition);

        if (target < oldPosition) {
            // Moving UP (e.g. 3 -> 1): rows in [target, old) step up by one.
            categoryRepository.shiftOrdersUp(target, oldPosition, category.getId());
        } else if (target > oldPosition) {
            // Moving DOWN (e.g. 1 -> 3): rows in (old, target] step down by one.
            categoryRepository.shiftOrdersDown(oldPosition, target, category.getId());
        }

        category.setDisplayOrder(target);
    }

    /**
     * Closes the gap left by a deleted category.
     *
     * <p>The caller must have flushed the delete <em>before</em> calling this.
     * A soft delete is an UPDATE of the deleted flag, and the bulk shift does
     * not flush first - so if the delete is still pending, the shift sees the
     * dying row and decrements its position as well.
     */
    public void closeGapAfterDelete(int deletedPosition) {
        categoryRepository.shiftOrdersAfterDelete(deletedPosition);
    }
}