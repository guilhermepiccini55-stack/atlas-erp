CREATE TABLE admin_password_change_audit (
    id                BIGSERIAL PRIMARY KEY,
    administrator_id  BIGINT      NOT NULL REFERENCES users (id),
    affected_user_id   BIGINT      NOT NULL REFERENCES users (id),
    occurred_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason             VARCHAR(32) NOT NULL
        CHECK (reason IN ('USER_REQUEST', 'SECURITY_CONCERN', 'ACCOUNT_COMPROMISED')),
    operation_type     VARCHAR(32) NOT NULL
        CHECK (operation_type = 'ADMIN_PASSWORD_CHANGE')
);

CREATE INDEX idx_admin_password_change_audit_affected_user
    ON admin_password_change_audit (affected_user_id, occurred_at);
