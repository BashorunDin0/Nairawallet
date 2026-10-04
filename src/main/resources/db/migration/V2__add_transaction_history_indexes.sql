create index if not exists idx_wallet_transactions_source_created
on wallet_transactions (source_wallet_id, created_at desc);

create index if not exists idx_wallet_transactions_destination_created
on wallet_transactions (destination_wallet_id, created_at desc);