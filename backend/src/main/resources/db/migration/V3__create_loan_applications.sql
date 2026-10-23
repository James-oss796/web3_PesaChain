CREATE TABLE loan_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_id UUID NOT NULL REFERENCES borrower_profiles(user_id) ON DELETE RESTRICT,
    requested_amount NUMERIC(30, 2) NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decision_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    decision_at TIMESTAMPTZ,
    decision_rationale TEXT,
    rejection_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT loan_applications_amount_check CHECK (requested_amount > 0),
    CONSTRAINT loan_applications_purpose_check CHECK (length(trim(purpose)) BETWEEN 1 AND 1000),
    CONSTRAINT loan_applications_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT loan_applications_decision_consistency_check CHECK (
        (status = 'PENDING' AND decision_by IS NULL AND decision_at IS NULL
            AND decision_rationale IS NULL AND rejection_reason IS NULL)
        OR
        (status = 'APPROVED' AND decision_by IS NOT NULL AND decision_at IS NOT NULL
            AND decision_rationale IS NOT NULL AND rejection_reason IS NULL)
        OR
        (status = 'REJECTED' AND decision_by IS NOT NULL AND decision_at IS NOT NULL
            AND decision_rationale IS NOT NULL AND rejection_reason IS NOT NULL
            AND length(trim(rejection_reason)) BETWEEN 1 AND 1000)
    )
);

CREATE INDEX loan_applications_borrower_submitted_idx
    ON loan_applications (borrower_id, submitted_at DESC);
CREATE INDEX loan_applications_status_submitted_idx
    ON loan_applications (status, submitted_at DESC);
