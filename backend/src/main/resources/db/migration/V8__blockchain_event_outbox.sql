ALTER TABLE loan_applications
    ADD COLUMN blockchain_aggregate_key BYTEA,
    ADD CONSTRAINT loan_applications_blockchain_key_length
        CHECK (blockchain_aggregate_key IS NULL OR octet_length(blockchain_aggregate_key) = 32);

CREATE TABLE blockchain_outbox (
    event_id UUID PRIMARY KEY,
    source_type VARCHAR(20) NOT NULL CHECK (source_type IN ('APPLICATION','DISBURSEMENT','REPAYMENT','LOAN')),
    source_id UUID NOT NULL,
    aggregate_type VARCHAR(20) NOT NULL CHECK (aggregate_type IN ('APPLICATION','LOAN')),
    aggregate_id UUID NOT NULL,
    aggregate_key BYTEA NOT NULL CHECK (octet_length(aggregate_key) = 32),
    event_type VARCHAR(32) NOT NULL CHECK (event_type IN (
        'LOAN_APPROVED','LOAN_REJECTED','LOAN_DISBURSED','REPAYMENT_RECORDED','LOAN_COMPLETED'
    )),
    amount_minor_units NUMERIC(30,0) NOT NULL DEFAULT 0 CHECK (amount_minor_units >= 0),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','PROCESSING','SUBMITTED','RECORDED','FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    retryable BOOLEAN NOT NULL DEFAULT TRUE,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_owner UUID,
    lease_until TIMESTAMPTZ,
    transaction_hash VARCHAR(66),
    block_number NUMERIC(78,0),
    confirmation_count INTEGER NOT NULL DEFAULT 0 CHECK (confirmation_count >= 0),
    submitted_at TIMESTAMPTZ,
    recorded_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT blockchain_outbox_source_event_unique UNIQUE (source_type,source_id,event_type),
    CONSTRAINT blockchain_outbox_amount_semantics CHECK (
        (event_type IN ('LOAN_DISBURSED','REPAYMENT_RECORDED') AND amount_minor_units > 0)
        OR (event_type NOT IN ('LOAN_DISBURSED','REPAYMENT_RECORDED') AND amount_minor_units = 0)
    ),
    CONSTRAINT blockchain_outbox_transaction_hash_format CHECK (
        transaction_hash IS NULL OR transaction_hash ~ '^0x[0-9a-fA-F]{64}$'
    ),
    CONSTRAINT blockchain_outbox_recorded_evidence CHECK (
        status <> 'RECORDED' OR
        (transaction_hash IS NOT NULL AND block_number IS NOT NULL AND recorded_at IS NOT NULL AND confirmation_count > 0)
    ),
    CONSTRAINT blockchain_outbox_submitted_hash CHECK (status NOT IN ('SUBMITTED','RECORDED') OR transaction_hash IS NOT NULL)
);

CREATE INDEX blockchain_outbox_due_idx ON blockchain_outbox (next_attempt_at,created_at)
    WHERE status IN ('PENDING','FAILED','PROCESSING');
CREATE INDEX blockchain_outbox_aggregate_idx ON blockchain_outbox (aggregate_type,aggregate_id,occurred_at);

CREATE TABLE blockchain_event_attempts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES blockchain_outbox(event_id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    outcome VARCHAR(16) CHECK (outcome IN ('SUBMITTED','RECORDED','RETRY','FAILED','RECONCILED')),
    transaction_hash VARCHAR(66),
    block_number NUMERIC(78,0),
    confirmation_count INTEGER CHECK (confirmation_count IS NULL OR confirmation_count >= 0),
    error_code VARCHAR(64),
    UNIQUE (event_id,attempt_number)
);

CREATE INDEX blockchain_attempts_event_idx ON blockchain_event_attempts(event_id,attempt_number);

CREATE TABLE blockchain_reconciliation_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID REFERENCES blockchain_outbox(event_id) ON DELETE RESTRICT,
    chain_event_id VARCHAR(66),
    finding_code VARCHAR(48) NOT NULL CHECK (finding_code IN (
        'MISSING_ON_CHAIN','PAYLOAD_MISMATCH','DUPLICATE_CHAIN_EVENT','UNEXPECTED_CHAIN_EVENT','LOCAL_CHAIN_MISMATCH'
    )),
    transaction_hash VARCHAR(66),
    block_number NUMERIC(78,0),
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    occurrence_count INTEGER NOT NULL DEFAULT 1 CHECK (occurrence_count > 0),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT blockchain_finding_chain_event_id_format CHECK (
        chain_event_id IS NULL OR chain_event_id ~ '^0x[0-9a-fA-F]{64}$'
    ),
    CONSTRAINT blockchain_finding_transaction_hash_format CHECK (
        transaction_hash IS NULL OR transaction_hash ~ '^0x[0-9a-fA-F]{64}$'
    )
);

CREATE UNIQUE INDEX blockchain_findings_active_unique_idx
    ON blockchain_reconciliation_findings(COALESCE(event_id,'00000000-0000-0000-0000-000000000000'::uuid),chain_event_id,finding_code)
    WHERE resolved_at IS NULL;
CREATE INDEX blockchain_findings_unresolved_idx ON blockchain_reconciliation_findings(first_seen_at)
    WHERE resolved_at IS NULL;
