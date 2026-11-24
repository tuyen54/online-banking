-- V4: Add balance to bank_accounts and expand audit_logs http_method column

ALTER TABLE audit_logs MODIFY COLUMN http_method VARCHAR(20) NOT NULL;

ALTER TABLE bank_accounts ADD COLUMN balance DECIMAL(19,2) NOT NULL DEFAULT 0.00;

