package com.qr_ordering_system.app.menu;

import com.qr_ordering_system.app.menu.dto.request.CategoryCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.CategoryUpdateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemCreateRequest;
import com.qr_ordering_system.app.menu.dto.request.MenuItemUpdateRequest;
import com.qr_ordering_system.app.menu.dto.response.CategoryResponse;
import com.qr_ordering_system.app.menu.dto.response.MenuItemResponse;
import com.qr_ordering_system.app.menu.service.CategoryService;
import com.qr_ordering_system.app.menu.service.MenuItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Display order is the most intricate logic in the menu slice: positions are a
 * dense 1..n sequence per parent, maintained with bulk shift UPDATEs that have
 * to insert, move up, move down, compact after a delete, and compact the old
 * category on a cross-category move. It used to be verified only by a hand-run
 * Node script, so a regression here would have been found by a customer.
 *
 * Each test runs against a throwaway tenant (see {@link TenantScopedTestSupport}),
 * so sequences start empty and assertions are absolute rather than relative to
 * whatever else is in the dev database.
 */
class MenuDisplayOrderTest extends TenantScopedTestSupport {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private MenuItemService menuItemService;

    // ── Categories ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("the first category with no requested position gets position 1, not 0")
    void firstCategoryIsPositionOne() {
        // Regression: findMaxDisplayOrder used to coalesce to -1, so "append"
        // produced position 0 - a value the update endpoint rejects as
        // @Positive, leaving that category permanently un-reorderable.
        assertEquals(1, createCategory("cat-a").displayOrder());
    }

    @Test
    @DisplayName("appending keeps the sequence dense: 1, 2, 3")
    void appendingKeepsSequenceDense() {
        createCategory("cat-a");
        createCategory("cat-b");
        CategoryResponse c = createCategory("cat-c");

        assertEquals(List.of(1, 2, 3), allCategoryPositions());
        assertEquals(3, c.displayOrder());
    }

    @Test
    @DisplayName("inserting at position 1 pushes everything else down")
    void insertingAtTheTopShiftsTheRest() {
        CategoryResponse a = createCategory("cat-a");
        CategoryResponse b = createCategory("cat-b");
        CategoryResponse c = createCategory("cat-c");

        CategoryResponse x = createCategoryAt("cat-x", 1);

        assertEquals(1, x.displayOrder());
        assertEquals(2, reload(a.id()).displayOrder());
        assertEquals(3, reload(b.id()).displayOrder());
        assertEquals(4, reload(c.id()).displayOrder());
        assertEquals(List.of(1, 2, 3, 4), allCategoryPositions());
    }

    @Test
    @DisplayName("moving a category up renumbers the range it jumped over")
    void movingUpShiftsTheRange() {
        CategoryResponse a = createCategory("cat-a");
        CategoryResponse b = createCategory("cat-b");
        CategoryResponse c = createCategory("cat-c");

        moveCategory(c.id(), "cat-c", 1);

        assertEquals(1, reload(c.id()).displayOrder());
        assertEquals(2, reload(a.id()).displayOrder());
        assertEquals(3, reload(b.id()).displayOrder());
        assertEquals(List.of(1, 2, 3), allCategoryPositions());
    }

    @Test
    @DisplayName("moving a category back down restores the original order")
    void movingDownShiftsTheRange() {
        CategoryResponse a = createCategory("cat-a");
        CategoryResponse b = createCategory("cat-b");
        CategoryResponse c = createCategory("cat-c");

        moveCategory(c.id(), "cat-c", 1);
        moveCategory(c.id(), "cat-c", 3);

        assertEquals(1, reload(a.id()).displayOrder());
        assertEquals(2, reload(b.id()).displayOrder());
        assertEquals(3, reload(c.id()).displayOrder());
        assertEquals(List.of(1, 2, 3), allCategoryPositions());
    }

    @Test
    @DisplayName("an out-of-range position is clamped to the end, never left as a hole")
    void outOfRangePositionIsClamped() {
        createCategory("cat-a");
        CategoryResponse b = createCategory("cat-b");

        // Position 99 in a two-category list becomes "the end".
        moveCategory(b.id(), "cat-b", 99);

        assertEquals(2, reload(b.id()).displayOrder());
        assertEquals(List.of(1, 2), allCategoryPositions());
    }

    @Test
    @DisplayName("deleting a category closes the gap it leaves behind")
    void deletingCompactsTheSequence() {
        CategoryResponse a = createCategory("cat-a");
        CategoryResponse b = createCategory("cat-b");
        CategoryResponse c = createCategory("cat-c");

        inTenant(() -> categoryService.delete(b.id()));

        assertEquals(1, reload(a.id()).displayOrder());
        assertEquals(2, reload(c.id()).displayOrder(),
                "c moves up into b's slot instead of staying at 3 with a hole at 2");
        assertEquals(List.of(1, 2), allCategoryPositions());
    }

    // ── Menu items ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("items are ordered 1..n inside their category")
    void itemsAreOrderedWithinCategory() {
        Long categoryId = createCategory("cat-items").id();

        createItem(categoryId, "item-1");
        createItem(categoryId, "item-2");
        createItem(categoryId, "item-3");

        assertEquals(List.of("item-1", "item-2", "item-3"), itemNames(categoryId));
        assertEquals(List.of(1, 2, 3), itemPositions(categoryId));
    }

    @Test
    @DisplayName("inserting an item at position 1 renumbers its siblings")
    void insertingItemAtTheTopShiftsTheRest() {
        Long categoryId = createCategory("cat-items").id();

        createItem(categoryId, "item-1");
        createItem(categoryId, "item-2");

        createItemAt(categoryId, "item-0", 1);

        assertEquals(List.of("item-0", "item-1", "item-2"), itemNames(categoryId));
        assertEquals(List.of(1, 2, 3), itemPositions(categoryId));
    }

    @Test
    @DisplayName("moving an item up then down restores the category")
    void movingItemWithinCategory() {
        Long categoryId = createCategory("cat-items").id();

        createItem(categoryId, "item-1");
        createItem(categoryId, "item-2");
        createItem(categoryId, "item-3");

        Long third = findItemId(categoryId, "item-3");
        renameItem(third, categoryId, "item-3", 1);
        assertEquals(List.of("item-3", "item-1", "item-2"), itemNames(categoryId));

        renameItem(third, categoryId, "item-3", 3);
        assertEquals(List.of("item-1", "item-2", "item-3"), itemNames(categoryId));
        assertEquals(List.of(1, 2, 3), itemPositions(categoryId));
    }

    @Test
    @DisplayName("moving an item to another category compacts the one it left")
    void movingItemAcrossCategoriesCompactsTheOldOne() {
        Long from = createCategory("cat-from").id();
        Long to = createCategory("cat-to").id();

        createItem(from, "item-move");
        createItem(from, "item-stay-a");
        createItem(from, "item-stay-b");

        Long moving = findItemId(from, "item-move");
        inTenant(() -> menuItemService.update(moving, updateRequest(
                to, "item-move", null, null)));

        assertEquals(List.of("item-stay-a", "item-stay-b"), itemNames(from),
                "the category it left must be left dense");
        assertEquals(List.of(1, 2), itemPositions(from));
        assertEquals(List.of("item-move"), itemNames(to));
        assertEquals(List.of(1), itemPositions(to));
    }

    @Test
    @DisplayName("an update with no displayOrder does not renumber anything")
    void updateWithoutPositionLeavesTheOrderAlone() {
        Long categoryId = createCategory("cat-items").id();

        createItem(categoryId, "item-1");
        createItem(categoryId, "item-2");
        createItem(categoryId, "item-3");

        Long target = findItemId(categoryId, "item-2");
        inTenant(() -> menuItemService.update(target, updateRequest(
                categoryId, "item-2-renamed", null, null)));

        assertEquals(List.of("item-1", "item-2-renamed", "item-3"), itemNames(categoryId));
        assertEquals(List.of(1, 2, 3), itemPositions(categoryId));
    }

    @Test
    @DisplayName("deleting an item closes the gap it leaves behind")
    void deletingItemCompactsTheCategory() {
        Long categoryId = createCategory("cat-items").id();

        createItem(categoryId, "item-1");
        createItem(categoryId, "item-2");
        createItem(categoryId, "item-3");

        inTenant(() -> menuItemService.delete(findItemId(categoryId, "item-2")));

        assertEquals(List.of("item-1", "item-3"), itemNames(categoryId));
        assertEquals(List.of(1, 2), itemPositions(categoryId),
                "no hole where the deleted item used to be");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private CategoryResponse createCategory(String name) {
        return inTenant(() -> categoryService.create(new CategoryCreateRequest(names(name), null)));
    }

    private CategoryResponse createCategoryAt(String name, int position) {
        return inTenant(() ->
                categoryService.create(new CategoryCreateRequest(names(name), position)));
    }

    private void moveCategory(Long id, String name, int position) {
        inTenant(() ->
                categoryService.update(id, new CategoryUpdateRequest(names(name), position)));
    }

    private CategoryResponse reload(Long id) {
        return inTenant(() -> categoryService.getById(id));
    }

    private List<Integer> allCategoryPositions() {
        return inTenant(() -> categoryService.getAll().stream()
                .map(CategoryResponse::displayOrder)
                .toList());
    }

    private void createItem(Long categoryId, String name) {
        inTenant(() -> menuItemService.create(new MenuItemCreateRequest(
                categoryId, names(name), emptyMap(), PRICE, null, null, null, null)));
    }

    private void createItemAt(Long categoryId, String name, int position) {
        inTenant(() -> menuItemService.create(new MenuItemCreateRequest(
                categoryId, names(name), emptyMap(), PRICE, null, position, null, null)));
    }

    private void renameItem(Long id, Long categoryId, String name, Integer position) {
        inTenant(() -> menuItemService.update(id, updateRequest(categoryId, name, position, null)));
    }

    private MenuItemUpdateRequest updateRequest(Long categoryId, String name,
                                                Integer position, Boolean isActive) {
        return new MenuItemUpdateRequest(
                categoryId, names(name), emptyMap(), PRICE, null, position, isActive, null);
    }

    private Long findItemId(Long categoryId, String name) {
        return inTenant(() -> menuItemService.getAllByCategory(categoryId).stream()
                .filter(i -> name.equals(i.localizedNames().get("en")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no item named " + name))
                .id());
    }

    /** Item names in the order the API returns them. */
    private List<String> itemNames(Long categoryId) {
        return inTenant(() -> menuItemService.getAllByCategory(categoryId).stream()
                .map(i -> i.localizedNames().get("en"))
                .toList());
    }

    private List<Integer> itemPositions(Long categoryId) {
        return inTenant(() -> menuItemService.getAllByCategory(categoryId).stream()
                .map(MenuItemResponse::displayOrder)
                .toList());
    }

    private static final BigDecimal PRICE = new BigDecimal("10.00");
}
