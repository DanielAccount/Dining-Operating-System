-- =====================================================
-- Categories
-- =====================================================
CREATE TABLE categories
(
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT      NOT NULL REFERENCES tenants (id),

    localized_names JSONB       NOT NULL DEFAULT '{}'::jsonb,
    display_order   INTEGER     NOT NULL DEFAULT 0,

    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ          DEFAULT now(),
    created_by      BIGINT,
    updated_by      BIGINT,
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_category_tenant
    ON categories (tenant_id);

CREATE INDEX idx_category_display_order
    ON categories (display_order);


-- =====================================================
-- Menu Items
-- =====================================================
CREATE TABLE menu_items
(
    id                     BIGINT PRIMARY KEY,
    tenant_id              BIGINT        NOT NULL REFERENCES tenants (id),
    category_id            BIGINT        NOT NULL REFERENCES categories (id),

    localized_names        JSONB         NOT NULL DEFAULT '{}'::jsonb,
    localized_descriptions JSONB                  DEFAULT '{}'::jsonb,
    base_price             NUMERIC(19, 2) NOT NULL CHECK (base_price >= 0),
    image_url              TEXT,
    is_active              BOOLEAN       NOT NULL DEFAULT TRUE,
    is_in_stock            BOOLEAN       NOT NULL DEFAULT TRUE,

    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ            DEFAULT now(),
    created_by             BIGINT,
    updated_by             BIGINT,
    is_deleted             BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_menu_item_category
    ON menu_items (category_id);

CREATE INDEX idx_menu_item_tenant_active
    ON menu_items (tenant_id, is_active);

CREATE INDEX idx_menu_item_tenant_stock
    ON menu_items (tenant_id, is_in_stock);


-- =====================================================
-- Modifier Groups
-- =====================================================
CREATE TABLE modifier_groups
(
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT      NOT NULL REFERENCES tenants (id),

    localized_names JSONB       NOT NULL DEFAULT '{}'::jsonb,
    is_required     BOOLEAN     NOT NULL DEFAULT FALSE,
    min_select      INTEGER     NOT NULL DEFAULT 0,
    max_select      INTEGER     NOT NULL DEFAULT 1,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,

    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ          DEFAULT now(),
    created_by      BIGINT,
    updated_by      BIGINT,
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE,

    CHECK (min_select <= max_select),
    CHECK (is_required = FALSE OR min_select >= 1)
);

CREATE INDEX idx_modifier_group_tenant
    ON modifier_groups (tenant_id);


-- =====================================================
-- Modifier Options
-- =====================================================
CREATE TABLE modifier_options
(
    id                BIGINT PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL REFERENCES tenants (id),
    modifier_group_id BIGINT        NOT NULL REFERENCES modifier_groups (id),

    localized_names   JSONB         NOT NULL DEFAULT '{}'::jsonb,
    extra_price       NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (extra_price >= 0),
    is_available      BOOLEAN       NOT NULL DEFAULT TRUE,

    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ            DEFAULT now(),
    created_by        BIGINT,
    updated_by        BIGINT,
    is_deleted        BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_modifier_option_tenant
    ON modifier_options (tenant_id);

CREATE INDEX idx_modifier_option_group
    ON modifier_options (modifier_group_id);


-- =====================================================
-- Menu Item Modifier Groups
-- Links menu items to modifier groups
-- =====================================================
CREATE TABLE menu_item_modifier_groups
(
    id                BIGINT PRIMARY KEY,
    tenant_id         BIGINT      NOT NULL REFERENCES tenants (id),
    menu_item_id      BIGINT      NOT NULL REFERENCES menu_items (id),
    modifier_group_id BIGINT      NOT NULL REFERENCES modifier_groups (id),

    display_order     INTEGER     NOT NULL DEFAULT 0,
    required_override BOOLEAN,
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE,

    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ          DEFAULT now(),
    created_by        BIGINT,
    updated_by        BIGINT,
    is_deleted        BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_mimg_tenant
    ON menu_item_modifier_groups (tenant_id);

CREATE INDEX idx_mimg_menu_item
    ON menu_item_modifier_groups (menu_item_id);

CREATE INDEX idx_mimg_modifier_group
    ON menu_item_modifier_groups (modifier_group_id);

CREATE UNIQUE INDEX uk_mimg_menu_item_group
    ON menu_item_modifier_groups (menu_item_id, modifier_group_id)
    WHERE is_deleted = FALSE;


-- =====================================================
-- Branch Menu Items
-- Branch-level availability and price override
-- =====================================================
CREATE TABLE branch_menu_items
(
    id             BIGINT PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL REFERENCES tenants (id),
    branch_id      BIGINT        NOT NULL REFERENCES branches (id),
    menu_item_id   BIGINT        NOT NULL REFERENCES menu_items (id),

    price_override NUMERIC(19, 2) CHECK (price_override IS NULL OR price_override >= 0),
    is_available   BOOLEAN       NOT NULL DEFAULT TRUE,

    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ            DEFAULT now(),
    created_by     BIGINT,
    updated_by     BIGINT,
    is_deleted     BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_bmi_tenant
    ON branch_menu_items (tenant_id);

CREATE INDEX idx_bmi_branch
    ON branch_menu_items (branch_id);

CREATE INDEX idx_bmi_menu_item
    ON branch_menu_items (menu_item_id);

CREATE UNIQUE INDEX uk_bmi_branch_menu_item
    ON branch_menu_items (branch_id, menu_item_id)
    WHERE is_deleted = FALSE;