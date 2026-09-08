--These are not strictly required, but they help common joins.

CREATE INDEX IF NOT EXISTS idx_user_branch
    ON users (branch_id);

CREATE INDEX IF NOT EXISTS idx_user_role
    ON users (role_id);

CREATE INDEX IF NOT EXISTS idx_branch_settings_tenant
    ON branch_settings (tenant_id);

CREATE INDEX IF NOT EXISTS idx_tenant_subscription_plan
    ON tenant_subscriptions (plan_id);

CREATE INDEX IF NOT EXISTS idx_service_request_order
    ON service_requests (order_id);

CREATE INDEX IF NOT EXISTS idx_service_request_payment
    ON service_requests (payment_id);