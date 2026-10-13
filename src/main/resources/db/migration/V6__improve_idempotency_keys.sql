
-- Existing idempotency records can be discarded in this development database.
DELETE FROM idempotency_keys;

-- Remove the old global uniqueness constraint and redundant index.
ALTER TABLE idempotency_keys
DROP CONSTRAINT IF EXISTS idempotency_keys_idempotency_key_key;

DROP INDEX IF EXISTS idx_idempotency_key;

-- Identify the owner and operation associated with each key.
ALTER TABLE idempotency_keys
    ADD COLUMN user_id BIGINT NOT NULL,
    ADD COLUMN operation_type VARCHAR(30) NOT NULL,
    ADD COLUMN request_hash VARCHAR(64) NOT NULL,
    ADD COLUMN transaction_id BIGINT,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING';

-- A key belongs to an existing user.
ALTER TABLE idempotency_keys
    ADD CONSTRAINT fk_idempotency_user
        FOREIGN KEY (user_id) REFERENCES users(id);

-- Link a completed idempotent request to its original transaction.
ALTER TABLE idempotency_keys
    ADD CONSTRAINT fk_idempotency_transaction
        FOREIGN KEY (transaction_id) REFERENCES wallet_transactions(id);

-- The same user can use different keys for different operation types.
ALTER TABLE idempotency_keys
    ADD CONSTRAINT uk_idempotency_user_operation_key
        UNIQUE (user_id, operation_type, idempotency_key);

-- A transaction can be linked to at most one idempotency record.
CREATE UNIQUE INDEX uk_idempotency_transaction
    ON idempotency_keys(transaction_id)
    WHERE transaction_id IS NOT NULL;

-- Support cleanup of expired keys.
CREATE INDEX idx_idempotency_expire_at
    ON idempotency_keys(expire_at);
