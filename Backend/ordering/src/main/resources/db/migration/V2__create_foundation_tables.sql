-- =====================================================
-- Tenants
-- =====================================================
CREATE TABLE tenants
(
    id            BIGINT PRIMARY KEY,
    business_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(64),
    address       VARCHAR(255),
    timezone      VARCHAR(64)  NOT NULL DEFAULT 'Asia/Yangon',
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TRIAL', 'TRIAL_EXPIRED', 'CANCELLED')),

    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ           DEFAULT now(),
    created_by    BIGINT,
    updated_by    BIGINT,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_tenant_business_name
    ON tenants (business_name);


-- =====================================================
-- Branches
-- =====================================================
CREATE TABLE branches
(
    id             BIGINT PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES tenants (id),
    name           VARCHAR(255) NOT NULL,
    address        VARCHAR(255),
    contact_number VARCHAR(64),
    timezone       VARCHAR(64),
    status         VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'TEMPORARILY_CLOSED', 'PERMANENTLY_CLOSED')),

    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ           DEFAULT now(),
    created_by     BIGINT,
    updated_by     BIGINT,
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_branch_tenant
    ON branches (tenant_id);


-- =====================================================
-- Branch Settings
-- =====================================================
CREATE TABLE branch_settings
(
    id                             BIGINT PRIMARY KEY,
    tenant_id                      BIGINT       NOT NULL REFERENCES tenants (id),
    branch_id                      BIGINT       NOT NULL REFERENCES branches (id),

    tax_rate                       NUMERIC(5, 2) NOT NULL DEFAULT 0,
    service_charge_rate            NUMERIC(5, 2) NOT NULL DEFAULT 0,
    invoice_prefix                 VARCHAR(64),
    auto_accept_orders             BOOLEAN      NOT NULL DEFAULT TRUE,
    require_payment_before_kitchen BOOLEAN      NOT NULL DEFAULT FALSE,

    version                        BIGINT       NOT NULL DEFAULT 0,
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ           DEFAULT now(),
    created_by                     BIGINT,
    updated_by                     BIGINT,
    is_deleted                     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_branch_settings_branch
    ON branch_settings (branch_id)
    WHERE is_deleted = FALSE;


-- =====================================================
-- Roles
-- =====================================================
CREATE TABLE roles
(
    id          BIGINT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenants (id),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),

    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ           DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uk_role_tenant_name
    ON roles (tenant_id, name)
    WHERE is_deleted = FALSE;


-- =====================================================
-- Permissions
-- Global table, not tenant-scoped
-- =====================================================
CREATE TABLE permissions
(
    id          BIGINT PRIMARY KEY,
    code        VARCHAR(100) NOT NULL,
    description VARCHAR(255),

    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ           DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_permission_code
    ON permissions (code);

CREATE UNIQUE INDEX uk_permission_code
    ON permissions (code)
    WHERE is_deleted = FALSE;


-- =====================================================
-- Role Permissions
-- Join table between roles and permissions
-- =====================================================
CREATE TABLE role_permissions
(
    role_id       BIGINT NOT NULL REFERENCES roles (id),
    permission_id BIGINT NOT NULL REFERENCES permissions (id)
);

ALTER TABLE role_permissions
    ADD CONSTRAINT uk_role_permission PRIMARY KEY (role_id, permission_id);


-- =====================================================
-- Users
-- =====================================================
CREATE TABLE users
(
    id                    BIGINT PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL REFERENCES tenants (id),
    branch_id             BIGINT REFERENCES branches (id),
    role_id               BIGINT       NOT NULL REFERENCES roles (id),

    username              VARCHAR(100) NOT NULL,
    email                 CITEXT       NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    display_name          VARCHAR(255),
    phone_number          VARCHAR(64),
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at         TIMESTAMPTZ,
    preferred_language    VARCHAR(32),

    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,

    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ           DEFAULT now(),
    created_by            BIGINT,
    updated_by            BIGINT,
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_user_tenant
    ON users (tenant_id);

CREATE INDEX idx_user_email_lookup
    ON users (email);

CREATE UNIQUE INDEX uk_user_email
    ON users (email)
    WHERE is_deleted = FALSE;

CREATE UNIQUE INDEX uk_user_tenant_username
    ON users (tenant_id, username)
    WHERE is_deleted = FALSE;