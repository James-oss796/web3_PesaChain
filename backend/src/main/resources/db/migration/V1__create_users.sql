CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT users_role_check CHECK (role IN ('BORROWER', 'LOAN_OFFICER', 'ADMINISTRATOR')),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE UNIQUE INDEX users_email_lower_unique ON users (lower(email));
