-- =====================================================
-- Menu item ordering
-- =====================================================
-- Position of an item WITHIN its category, so an owner can order their own
-- menu (drinks before mains, most popular first, ...).
--
-- Mirrors the existing categories.display_order pattern: a 1-based, dense
-- sequence per parent, renumbered with shift queries on insert/move/delete.
-- Scoped per category rather than global, because the meaningful unit of
-- ordering is "which item comes first inside this category".
--
-- Deliberately NOT unique-indexed: the shift queries renumber rows in place,
-- so intermediate states would transiently violate uniqueness. categories
-- makes the same trade-off. Concurrency is handled in the service layer.

ALTER TABLE menu_items
    ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;

-- Backfill existing rows to a dense 1..n sequence per category, preserving
-- the current (id) order so no item visibly jumps around after the upgrade.
-- Soft-deleted rows keep the 0 default; Hibernate's @SoftDelete filter means
-- they are invisible to every query that reads display_order.
WITH ranked AS (SELECT id,
                      ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY id) AS rn
               FROM menu_items
               WHERE is_deleted = FALSE)
UPDATE menu_items m
SET display_order = ranked.rn
FROM ranked
WHERE m.id = ranked.id;

-- Supports the two hot reads: the management grid (category + order) and the
-- Phase 3 customer menu.
CREATE INDEX IF NOT EXISTS idx_menu_item_category_order
    ON menu_items (category_id, display_order);
