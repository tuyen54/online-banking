-- V5: Create bank_transactions and ledger_entries tables for double-entry bookkeeping

CREATE TABLE IF NOT EXISTS bank_transactions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    transaction_reference VARCHAR(40) NOT NULL,
    initiator_id BINARY(16) NOT NULL,
    source_account_id BINARY(16) NOT NULL,
    destination_account_id BINARY(16) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    CONSTRAINT uk_tx_reference UNIQUE (transaction_reference),
    CONSTRAINT uk_tx_initiator_idempotency UNIQUE (initiator_id, idempotency_key),
    CONSTRAINT fk_tx_initiator FOREIGN KEY (initiator_id) REFERENCES user_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_source_acc FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_dest_acc FOREIGN KEY (destination_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    INDEX idx_tx_source_acc (source_account_id),
    INDEX idx_tx_dest_acc (destination_account_id),
    INDEX idx_tx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ledger_entries (
    id BINARY(16) NOT NULL PRIMARY KEY,
    transaction_id BINARY(16) NOT NULL,
    bank_account_id BINARY(16) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_ledger_tx_entry UNIQUE (transaction_id, entry_type),
    CONSTRAINT fk_ledger_tx FOREIGN KEY (transaction_id) REFERENCES bank_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_acc FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    INDEX idx_ledger_acc (bank_account_id),
    INDEX idx_ledger_tx (transaction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

