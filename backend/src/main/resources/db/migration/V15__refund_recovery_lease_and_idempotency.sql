-- =============================================================================
-- NOVA PETZLIFE — Flyway Migration V15: Refund Recovery Lease, Idempotency & Bootstrap Lock
-- Authoritative schema additions for durable refund recovery and one-time bootstrap lock.
-- Compatible with MySQL 8.4 / 8.0 and H2 (no non-standard IF NOT EXISTS on ALTER/CREATE INDEX).
-- =============================================================================

ALTER TABLE refunds ADD COLUMN idempotency_key VARCHAR(100) UNIQUE;
ALTER TABLE refunds ADD COLUMN receipt VARCHAR(40) UNIQUE;
ALTER TABLE refunds ADD COLUMN claim_token VARCHAR(64);
ALTER TABLE refunds ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE refunds ADD COLUMN claim_expires_at DATETIME;
ALTER TABLE refunds ADD COLUMN recovery_status VARCHAR(50) DEFAULT 'PENDING';
ALTER TABLE refunds ADD COLUMN last_attempt_at DATETIME;

CREATE INDEX idx_refunds_recovery ON refunds (status, claim_expires_at, recovery_status);
CREATE INDEX idx_refunds_claim_token ON refunds (claim_token);

CREATE TABLE IF NOT EXISTS platform_bootstrap_lock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bootstrap_key VARCHAR(100) NOT NULL UNIQUE,
    bootstrapped_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    bootstrapped_by_ip VARCHAR(45),
    admin_email VARCHAR(150) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
