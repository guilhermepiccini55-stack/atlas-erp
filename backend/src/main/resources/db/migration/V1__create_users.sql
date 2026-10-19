CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(180) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(180),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'EMPLOYEE', 'VIEWER', 'GUEST')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'PENDING', 'INACTIVE'))
);

-- E-mail único apenas entre usuários não excluídos (soft delete)
CREATE UNIQUE INDEX uk_users_email_active ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);
CREATE INDEX idx_users_created_at ON users (created_at);
