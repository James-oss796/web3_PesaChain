ALTER TABLE users
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE audit_log
    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX users_role_status_idx ON users (role, status, created_at DESC);
