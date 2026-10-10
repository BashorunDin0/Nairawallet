ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_balance_transition
    CHECK (
        (
            entry_type = 'CREDIT'
            AND balance_after = balance_before + amount
        )
        OR
        (
            entry_type = 'DEBIT'
            AND balance_after = balance_before - amount
        )
    );
