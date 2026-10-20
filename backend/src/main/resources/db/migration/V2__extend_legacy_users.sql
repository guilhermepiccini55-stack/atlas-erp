DO $$
BEGIN
    IF EXISTS (
        SELECT lower(email)
        FROM users
        GROUP BY lower(email)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot create case-insensitive active-email uniqueness: duplicate email addresses already exist';
    END IF;
END
$$;

ALTER TABLE users
    DROP CONSTRAINT users_email_key;

ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'GUEST',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN created_by VARCHAR(255),
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN credential_state VARCHAR(30) NOT NULL DEFAULT 'LEGACY_UNVERIFIED',
    ADD CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'EMPLOYEE', 'VIEWER', 'GUEST')),
    ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'PENDING', 'INACTIVE')),
    ADD CONSTRAINT ck_users_credential_state CHECK (credential_state IN ('BCRYPT', 'LEGACY_UNVERIFIED'));

CREATE UNIQUE INDEX uk_users_email_active ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_created_at ON users (created_at);
