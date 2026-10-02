-- =====================================================
-- Stock is a per-branch fact, not a tenant-wide one
-- =====================================================
-- "Out of stock" is a statement about one kitchen, not about the whole tenant:
-- Mandalay running out of mango lassi says nothing about Yangon. The previous
-- model put is_in_stock on menu_items (tenant-wide), which made that
-- impossible to express - the only way to stop an item selling at one branch
-- was to mark it "not offered" there, which is a different thing entirely.
--
-- The resulting model is three distinct levels, each with its own question:
--   menu_items.is_active          -> is this dish on the menu at all?
--   branch_menu_items.is_available -> does THIS branch offer it?
--   branch_menu_items.is_in_stock  -> does THIS branch have it right now?
--
-- A sold-out item stays visible on the customer menu (greyed out, unorderable)
-- rather than disappearing, so customers are told the truth instead of
-- concluding the dish was discontinued.

ALTER TABLE branch_menu_items
    ADD COLUMN is_in_stock BOOLEAN NOT NULL DEFAULT TRUE;

-- Existing branch rows keep serving: default TRUE above, so no backfill needed.
-- Rows that do not exist yet already mean "follow global defaults", and the
-- service resolves a missing row to available + in stock.

-- The tenant-wide flag is now redundant and misleading, so it goes.
-- Postgres drops dependent indexes with the column.
ALTER TABLE menu_items DROP COLUMN is_in_stock;

-- Supports the customer menu read: one branch's rows, resolved in order.
-- idx_bmi_branch already covers the branch_id lookup itself.
CREATE INDEX IF NOT EXISTS idx_bmi_branch_stock
    ON branch_menu_items (branch_id, is_available, is_in_stock)
    WHERE is_deleted = FALSE;
