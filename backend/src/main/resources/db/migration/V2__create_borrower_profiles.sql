CREATE TABLE borrower_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    full_name VARCHAR(120) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT borrower_profiles_full_name_check CHECK (length(trim(full_name)) BETWEEN 2 AND 120),
    CONSTRAINT borrower_profiles_phone_check CHECK (length(trim(phone)) BETWEEN 7 AND 32)
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    actor_role VARCHAR(24),
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(48) NOT NULL,
    target_id UUID,
    result VARCHAR(16) NOT NULL DEFAULT 'SUCCEEDED',
    correlation_id VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT audit_actor_role_check CHECK (
        actor_role IS NULL OR actor_role IN ('BORROWER', 'LOAN_OFFICER', 'ADMINISTRATOR')
    ),
    CONSTRAINT audit_result_check CHECK (result IN ('SUCCEEDED', 'FAILED'))
);

CREATE INDEX audit_log_occurred_at_idx ON audit_log (occurred_at DESC);
CREATE INDEX audit_log_actor_occurred_at_idx ON audit_log (actor_id, occurred_at DESC);
