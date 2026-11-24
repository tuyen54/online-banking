-- V1: Security Audit Log schema
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    user_id VARCHAR(255) NULL,
    username VARCHAR(255) NULL,
    http_method VARCHAR(10) NOT NULL,
    request_uri VARCHAR(500) NOT NULL,
    status_code INT NOT NULL,
    remote_ip_address VARCHAR(45) NULL,
    description VARCHAR(1000) NULL,
    timestamp DATETIME NOT NULL,
    INDEX idx_audit_event_type (event_type),
    INDEX idx_audit_user_id (user_id),
    INDEX idx_audit_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

