-- =====================================================
-- Payments
-- =====================================================
CREATE TABLE payments
(
    id                         BIGINT PRIMARY KEY,
    tenant_id                  BIGINT        NOT NULL REFERENCES tenants (id),
    order_id                   BIGINT        NOT NULL REFERENCES orders (id),
    branch_id                  BIGINT        NOT NULL REFERENCES branches (id),

    amount                     NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    refunded_amount            NUMERIC(19, 2) NOT NULL DEFAULT 0,

    method                     VARCHAR(32)   NOT NULL
        CHECK (method IN ('CASH', 'KBZ_PAY', 'WAVE_PAY')),

    status                     VARCHAR(32)   NOT NULL
        CHECK (status IN (
                         'PENDING',
                         'AWAITING_VERIFICATION',
                         'PAID',
                         'FAILED',
                         'CANCELLED',
                         'EXPIRED',
                         'PARTIALLY_REFUNDED',
                         'REFUNDED'
            )),

    idempotency_key            VARCHAR(120),

    verified_by                BIGINT,
    verified_at                TIMESTAMPTZ,
    verification_remarks       VARCHAR(255),
    rejected_reason            VARCHAR(255),

    transaction_reference      VARCHAR(120),
    transaction_screenshot_url TEXT,
    paid_at                    TIMESTAMPTZ,

    version                    BIGINT        NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ            DEFAULT now(),
    created_by                 BIGINT,
    updated_by                 BIGINT,
    is_deleted                 BOOLEAN       NOT NULL DEFAULT FALSE,

    CHECK (refunded_amount >= 0 AND refunded_amount <= amount)
);

CREATE INDEX idx_payment_tenant
    ON payments (tenant_id);

CREATE INDEX idx_payment_order
    ON payments (order_id);

CREATE INDEX idx_payment_tenant_status
    ON payments (tenant_id, status);

CREATE INDEX idx_payment_branch_status
    ON payments (tenant_id, branch_id, status);

CREATE UNIQUE INDEX uk_payment_tenant_txn_ref
    ON payments (tenant_id, transaction_reference)
    WHERE is_deleted = FALSE
      AND transaction_reference IS NOT NULL;

CREATE UNIQUE INDEX uk_payment_tenant_idempotency
    ON payments (tenant_id, idempotency_key)
    WHERE is_deleted = FALSE
      AND idempotency_key IS NOT NULL;


-- =====================================================
-- Branch Payment Details
-- =====================================================
CREATE TABLE branch_payment_details
(
    id                    BIGINT PRIMARY KEY,
    tenant_id             BIGINT       NOT NULL REFERENCES tenants (id),
    branch_id             BIGINT       NOT NULL REFERENCES branches (id),

    method                VARCHAR(32)  NOT NULL
        CHECK (method IN ('CASH', 'KBZ_PAY', 'WAVE_PAY')),

    account_name          VARCHAR(255),
    account_phone_number  VARCHAR(64),
    merchant_qr_image_url TEXT,
    is_enabled            BOOLEAN      NOT NULL DEFAULT TRUE,

    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ           DEFAULT now(),
    created_by            BIGINT,
    updated_by            BIGINT,
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_bpd_tenant
    ON branch_payment_details (tenant_id);

CREATE INDEX idx_bpd_branch
    ON branch_payment_details (branch_id);

CREATE UNIQUE INDEX uk_bpd_branch_method
    ON branch_payment_details (branch_id, method)
    WHERE is_deleted = FALSE;


-- =====================================================
-- Subscription Plans
-- Global table, not tenant-scoped
-- =====================================================
CREATE TABLE subscription_plans
(
    id            BIGINT PRIMARY KEY,
    name          VARCHAR(120)   NOT NULL,
    monthly_price NUMERIC(19, 2) NOT NULL CHECK (monthly_price >= 0),
    max_branches  INTEGER CHECK (max_branches IS NULL OR max_branches > 0),
    max_users     INTEGER CHECK (max_users IS NULL OR max_users > 0),

    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ             DEFAULT now(),
    created_by    BIGINT,
    updated_by    BIGINT,
    is_deleted    BOOLEAN        NOT NULL DEFAULT FALSE
);


-- =====================================================
-- Tenant Subscriptions
-- =====================================================
CREATE TABLE tenant_subscriptions
(
    id                   BIGINT PRIMARY KEY,
    tenant_id            BIGINT       NOT NULL REFERENCES tenants (id),
    plan_id              BIGINT       NOT NULL REFERENCES subscription_plans (id),

    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'CANCELLED')),

    current_period_start TIMESTAMPTZ  NOT NULL DEFAULT now(),
    current_period_end   TIMESTAMPTZ  NOT NULL,

    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ           DEFAULT now(),
    created_by           BIGINT,
    updated_by           BIGINT,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_tenant_subscription_tenant
    ON tenant_subscriptions (tenant_id);

CREATE UNIQUE INDEX uk_tenant_subscription_active
    ON tenant_subscriptions (tenant_id)
    WHERE is_deleted = FALSE
      AND status = 'ACTIVE';


-- =====================================================
-- Tenant Payment Methods
-- =====================================================
CREATE TABLE tenant_payment_methods
(
    id         BIGINT PRIMARY KEY,
    tenant_id  BIGINT       NOT NULL REFERENCES tenants (id),

    method     VARCHAR(32)  NOT NULL
        CHECK (method IN ('CASH', 'KBZ_PAY', 'WAVE_PAY')),

    is_enabled BOOLEAN      NOT NULL DEFAULT TRUE,

    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ           DEFAULT now(),
    created_by BIGINT,
    updated_by BIGINT,
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_tenant_payment_method_tenant
    ON tenant_payment_methods (tenant_id);

CREATE UNIQUE INDEX uk_tpm_tenant_method
    ON tenant_payment_methods (tenant_id, method)
    WHERE is_deleted = FALSE;