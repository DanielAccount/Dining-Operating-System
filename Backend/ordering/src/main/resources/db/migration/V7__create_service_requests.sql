-- =====================================================
-- Service Requests
-- =====================================================
CREATE TABLE service_requests
(
    id               BIGINT PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL REFERENCES tenants (id),
    branch_id        BIGINT       NOT NULL REFERENCES branches (id),
    table_session_id BIGINT       NOT NULL REFERENCES table_sessions (id),
    order_id         BIGINT REFERENCES orders (id),
    payment_id       BIGINT REFERENCES payments (id),

    idempotency_key  VARCHAR(120),

    type             VARCHAR(32)  NOT NULL
        CHECK (type IN ('CALL_WAITER', 'REQUEST_BILL', 'PAY_CASH', 'PAY_DIGITAL_SUBMITTED')),

    status           VARCHAR(32)  NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),

    acknowledged_by  BIGINT,
    acknowledged_at  TIMESTAMPTZ,

    resolved_by      BIGINT,
    resolved_at      TIMESTAMPTZ,

    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ           DEFAULT now(),
    created_by       BIGINT,
    updated_by       BIGINT,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_service_request_tenant
    ON service_requests (tenant_id);

CREATE INDEX idx_service_request_session
    ON service_requests (table_session_id);

CREATE INDEX idx_service_request_status
    ON service_requests (tenant_id, branch_id, status);

CREATE UNIQUE INDEX uk_service_request_tenant_idempotency
    ON service_requests (tenant_id, idempotency_key)
    WHERE is_deleted = FALSE
      AND idempotency_key IS NOT NULL;