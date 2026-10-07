-- V4: Add database-level financial integrity constraints

ALTER TABLE wallet_transactions
ALTER COLUMN tx_reference TYPE VARCHAR(100);

ALTER TABLE wallet_transactions
    ADD CONSTRAINT chk_wallet_transaction_amount_positive
        CHECK (amount > 0);

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_balance_non_negative
        CHECK (balance >= 0);

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_amount_positive
        CHECK (amount > 0);

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_balance_before_non_negative
        CHECK (balance_before >= 0);

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_balance_after_non_negative
        CHECK (balance_after >= 0);