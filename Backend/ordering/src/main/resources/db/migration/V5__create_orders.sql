-- =====================================================
-- Orders
-- =====================================================
CREATE TABLE orders
(
    id                   BIGINT PRIMARY KEY,
    tenant_id            BIGINT        NOT NULL REFERENCES tenants (id),
    branch_id            BIGINT        NOT NULL REFERENCES branches (id),
    table_session_id     BIGINT REFERENCES table_sessions (id),

    order_type           VARCHAR(32)   NOT NULL DEFAULT 'DINE_IN'
        CHECK (order_type IN ('DINE_IN', 'TAKEAWAY')),
    source               VARCHAR(32)   NOT NULL DEFAULT 'QR_CUSTOMER'
        CHECK (source IN ('QR_CUSTOMER', 'WAITER', 'CASHIER')),

    placed_by            BIGINT,
    table_number_snapshot VARCHAR(64),
    customer_note        TEXT,
    customer_phone_number VARCHAR(64),

    idempotency_key      VARCHAR(120)  NOT NULL,
    invoice_number       VARCHAR(64),

    status               VARCHAR(32)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED')),

    cancelled_at         TIMESTAMPTZ,
    cancelled_by         BIGINT,
    cancel_reason        VARCHAR(255),

    subtotal             NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    tax_amount           NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    discount_amount      NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    service_charge       NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (service_charge >= 0),
    final_amount         NUMERIC(19, 2) NOT NULL DEFAULT 0,

    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ            DEFAULT now(),
    created_by           BIGINT,
    updated_by           BIGINT,
    is_deleted           BOOLEAN       NOT NULL DEFAULT FALSE,

    CHECK (
        (order_type = 'DINE_IN' AND table_session_id IS NOT NULL)
        OR
        (order_type = 'TAKEAWAY' AND table_session_id IS NULL)
    ),

    CHECK (
        final_amount = subtotal - discount_amount + tax_amount + service_charge
    )
);

CREATE INDEX idx_order_tenant_date
    ON orders (tenant_id, created_at);

CREATE INDEX idx_order_branch_date
    ON orders (branch_id, created_at);

CREATE INDEX idx_order_session
    ON orders (table_session_id);

CREATE INDEX idx_order_tenant_status
    ON orders (tenant_id, status);

CREATE UNIQUE INDEX uk_order_tenant_idempotency
    ON orders (tenant_id, idempotency_key)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_order_tenant_invoice
    ON orders (tenant_id, invoice_number)
    WHERE is_deleted = FALSE
      AND invoice_number IS NOT NULL;


-- =====================================================
-- Order Items
-- =====================================================
CREATE TABLE order_items
(
    id                     BIGINT PRIMARY KEY,
    tenant_id              BIGINT        NOT NULL REFERENCES tenants (id),
    order_id               BIGINT        NOT NULL REFERENCES orders (id),
    branch_id              BIGINT        NOT NULL REFERENCES branches (id),
    menu_item_id           BIGINT        NOT NULL REFERENCES menu_items (id),

    item_name_snapshot     JSONB         NOT NULL DEFAULT '{}'::jsonb,
    quantity               INTEGER       NOT NULL CHECK (quantity > 0),
    price_at_time_of_order NUMERIC(19, 2) NOT NULL CHECK (price_at_time_of_order >= 0),
    discount_amount        NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    line_total             NUMERIC(19, 2) NOT NULL CHECK (line_total >= 0),

    item_status            VARCHAR(32)   NOT NULL DEFAULT 'PENDING'
        CHECK (item_status IN ('PENDING', 'PREPARING', 'READY', 'SERVED', 'CANCELLED')),

    ready_at               TIMESTAMPTZ,
    served_at              TIMESTAMPTZ,

    version                BIGINT        NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ            DEFAULT now(),
    created_by             BIGINT,
    updated_by             BIGINT,
    is_deleted             BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_order_item_tenant
    ON order_items (tenant_id);

CREATE INDEX idx_order_item_order
    ON order_items (order_id);

CREATE INDEX idx_order_item_menu_item
    ON order_items (menu_item_id);

CREATE INDEX idx_order_item_branch_status
    ON order_items (tenant_id, branch_id, item_status, created_at);


-- =====================================================
-- Order Item Modifiers
-- Immutable audit-style row.
-- No version, no updated_at, no is_deleted.
-- =====================================================
CREATE TABLE order_item_modifiers
(
    id                BIGINT PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL REFERENCES tenants (id),
    order_item_id     BIGINT        NOT NULL REFERENCES order_items (id),
    modifier_option_id BIGINT REFERENCES modifier_options (id),

    modifier_name     VARCHAR(255)  NOT NULL,
    modifier_price    NUMERIC(19, 2) NOT NULL CHECK (modifier_price >= 0),

    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        BIGINT
);

CREATE INDEX idx_order_modifier_tenant
    ON order_item_modifiers (tenant_id);

CREATE INDEX idx_order_modifier_order_item
    ON order_item_modifiers (order_item_id);


-- =====================================================
-- Order Status History
-- Immutable audit-style row.
-- No version, no updated_at, no is_deleted.
-- =====================================================
CREATE TABLE order_status_history
(
    id         BIGINT PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL REFERENCES tenants (id),
    order_id   BIGINT       NOT NULL REFERENCES orders (id),

    status     VARCHAR(32)  NOT NULL
        CHECK (status IN ('PENDING', 'ACCEPTED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED')),

    remarks    TEXT,

    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by BIGINT
);

CREATE INDEX idx_order_history_tenant
    ON order_status_history (tenant_id);

CREATE INDEX idx_order_history_order
    ON order_status_history (order_id);