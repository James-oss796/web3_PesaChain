CREATE TABLE simulated_repayment_events (
    id UUID PRIMARY KEY,
    loan_id UUID NOT NULL REFERENCES loans(id) ON DELETE RESTRICT,
    borrower_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount NUMERIC(30,2) NOT NULL CHECK (amount > 0),
    idempotency_key UUID NOT NULL,
    signature CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','VERIFIED','RECORDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source_verified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    verified_at TIMESTAMPTZ,
    recorded_at TIMESTAMPTZ,
    CONSTRAINT simulated_event_request_unique UNIQUE (borrower_id,idempotency_key),
    CONSTRAINT simulated_event_verification_state CHECK (
        (status = 'PENDING' AND verified_by IS NULL AND verified_at IS NULL AND recorded_at IS NULL)
        OR (status = 'VERIFIED' AND verified_by IS NOT NULL AND verified_at IS NOT NULL AND recorded_at IS NULL)
        OR (status = 'RECORDED' AND verified_by IS NOT NULL AND verified_at IS NOT NULL AND recorded_at IS NOT NULL)
    )
);

CREATE INDEX simulated_events_loan_status_idx ON simulated_repayment_events (loan_id,status,created_at DESC);

CREATE TABLE repayments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL UNIQUE REFERENCES simulated_repayment_events(id) ON DELETE RESTRICT,
    loan_id UUID NOT NULL REFERENCES loans(id) ON DELETE RESTRICT,
    amount_applied NUMERIC(30,2) NOT NULL CHECK (amount_applied > 0),
    resulting_balance NUMERIC(30,2) NOT NULL CHECK (resulting_balance >= 0),
    recorded_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX repayments_loan_recorded_idx ON repayments (loan_id,recorded_at,id);
