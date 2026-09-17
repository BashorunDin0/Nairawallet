SET search_path TO public;

-- 1. Create Users Table
CREATE TABLE users (
id BIGSERIAL primary key,
full_name varchar(255) NOT NULL,
email VARCHAR(255) NOT NULL UNIQUE,
user_role VARCHAR(255) NOT NULL,
phone_number VARCHAR(255) UNIQUE,
created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_user_email ON users(email);

-- 2. Create Wallets Table (Linked to User)
CREATE TABLE wallets (
id BIGSERIAL PRIMARY KEY,
balance NUMERIC(19, 2) NOT NULL,
status VARCHAR(50),
version BIGINT NOT NULL,
currency varchar(3),
user_id BIGINT NOT NULL UNIQUE,
CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 3. Create Transactions Table
CREATE TABLE wallet_transactions(
id BIGSERIAL PRIMARY KEY,
wallet_id BIGINT NOT NULL,
tx_reference VARCHAR(255) NOT NULL UNIQUE,
transaction_type VARCHAR(50),
amount NUMERIC(19,2) NOT NULL,
status VARCHAR(255),
narration VARCHAR(255) NOT NULL,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
CONSTRAINT fk_wallet_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE CASCADE

);
CREATE INDEX idx_reference on wallet_transactions(tx_reference);
CREATE INDEX idx_wallet_transaction_wallet_id ON wallet_transactions(wallet_id);

-- 4. Create LedgerEntry Table (Linked to Wallet and Transaction)
CREATE TABLE ledger_entries(
id BIGSERIAL PRIMARY KEY,
wallet_id BIGINT NOT NULL,
transaction_id BIGINT NOT NULL,
amount NUMERIC(19,2) NOT NULL,
balance_before NUMERIC(19,2) NOT NULL,
balance_after NUMERIC(19, 2) NOT NULL,
entry_type VARCHAR(50),
narration VARCHAR(255),
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
CONSTRAINT fk_ledger_entries_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(id),
CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (transaction_id) REFERENCES wallet_transactions(id)
);
CREATE INDEX idx_ledger_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_wallet_id ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_created_at ON ledger_entries(created_at);

-- 5. Create IdempotencyKey Table
CREATE TABLE idempotency_keys(
id BIGSERIAL PRIMARY KEY,
idempotency_key VARCHAR(255) NOT NULL UNIQUE,
created_at TIMESTAMP WITH TIME ZONE NOT NULL,
expire_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_idempotency_key on idempotency_keys(idempotency_key);