-- Allows an Administrator to deliberately redrive an exhausted FAILED outbox event.
-- Attempt history and the stable event identifier remain intact.
ALTER TABLE blockchain_outbox
    ADD COLUMN manual_retry_requested BOOLEAN NOT NULL DEFAULT FALSE;
