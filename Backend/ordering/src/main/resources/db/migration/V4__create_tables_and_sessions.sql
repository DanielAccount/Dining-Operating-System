-- =====================================================
-- Restaurant Tables
-- =====================================================
CREATE TABLE restaurant_tables
(
    id           BIGINT PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenants (id),
    branch_id    BIGINT       NOT NULL REFERENCES branches (id),

    table_number VARCHAR(64)  NOT NULL,
    qr_slug      VARCHAR(120) NOT NULL,

    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ           DEFAULT now(),
    created_by   BIGINT,
    updated_by   BIGINT,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_table_branch
    ON restaurant_tables (branch_id);

CREATE UNIQUE INDEX uk_table_branch_number
    ON restaurant_tables (branch_id, table_number)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_table_qr_slug
    ON restaurant_tables (qr_slug)
    WHERE is_deleted = FALSE;


-- =====================================================
-- Table Sessions
-- =====================================================
CREATE TABLE table_sessions
(
    id            BIGINT PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL REFERENCES tenants (id),
    branch_id     BIGINT       NOT NULL REFERENCES branches (id),
    table_id      BIGINT       NOT NULL REFERENCES restaurant_tables (id),

    session_code  VARCHAR(64)  NOT NULL,
    session_state VARCHAR(32)  NOT NULL DEFAULT 'CUSTOMER_ORDERING'
        CHECK (session_state IN ('CUSTOMER_ORDERING', 'AWAITING_CLEANUP', 'CLOSED')),

    opened_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ,

    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ           DEFAULT now(),
    created_by    BIGINT,
    updated_by    BIGINT,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_table_session_tenant
    ON table_sessions (tenant_id);

CREATE INDEX idx_table_session_table
    ON table_sessions (table_id);

CREATE INDEX idx_table_session_state
    ON table_sessions (tenant_id, session_state);

CREATE UNIQUE INDEX uk_table_session_code
    ON table_sessions (table_id, session_code)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_table_session_open
    ON table_sessions (table_id)
    WHERE session_state <> 'CLOSED'
      AND is_deleted = FALSE;