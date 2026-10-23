ALTER TABLE loan_applications
    ADD COLUMN idempotency_key UUID;

CREATE UNIQUE INDEX loan_applications_borrower_idempotency_key_idx
    ON loan_applications (borrower_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
