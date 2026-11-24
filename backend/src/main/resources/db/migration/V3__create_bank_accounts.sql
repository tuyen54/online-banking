-- V3: Bank Accounts schema

CREATE TABLE IF NOT EXISTS bank_accounts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    account_number VARCHAR(30) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    account_type VARCHAR(20) NOT NULL DEFAULT 'CHECKING',
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    opened_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    version BIGINT NULL,
    CONSTRAINT uk_bank_accounts_number UNIQUE (account_number),
    CONSTRAINT fk_bank_accounts_owner FOREIGN KEY (owner_id) REFERENCES user_profiles (id) ON DELETE RESTRICT,
    INDEX idx_ba_owner_id (owner_id),
    INDEX idx_ba_status (account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

