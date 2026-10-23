-- Requested term remains nullable for compatibility with any applications created before
-- term collection existed. The application service requires it for all new submissions.
ALTER TABLE loan_applications
    ADD COLUMN requested_term_months INTEGER,
    ADD COLUMN approved_principal NUMERIC(30, 2),
    ADD COLUMN approved_term_months INTEGER,
    ADD COLUMN approved_flat_rate_percent NUMERIC(30, 10),
    ADD COLUMN calculated_interest NUMERIC(30, 2),
    ADD COLUMN total_repayable NUMERIC(30, 2);

ALTER TABLE loan_applications
    ADD CONSTRAINT loan_applications_requested_term_check
        CHECK (requested_term_months IS NULL OR requested_term_months > 0),
    ADD CONSTRAINT loan_applications_approved_financials_check
        CHECK (
            status <> 'APPROVED'
            OR (
                approved_principal IS NOT NULL
                AND approved_term_months IS NOT NULL
                AND approved_flat_rate_percent IS NOT NULL
                AND calculated_interest IS NOT NULL
                AND total_repayable IS NOT NULL
                AND approved_principal > 0
                AND approved_term_months > 0
                AND approved_flat_rate_percent >= 0
                AND calculated_interest >= 0
                AND total_repayable = approved_principal + calculated_interest
            )
        ) NOT VALID,
    ADD CONSTRAINT loan_applications_nonapproved_financials_check
        CHECK (
            status = 'APPROVED'
            OR (
                approved_principal IS NULL
                AND approved_term_months IS NULL
                AND approved_flat_rate_percent IS NULL
                AND calculated_interest IS NULL
                AND total_repayable IS NULL
            )
        ) NOT VALID;

CREATE INDEX loan_applications_decision_queue_idx
    ON loan_applications (submitted_at, id)
    WHERE status = 'PENDING';
