-- =============================================================================
-- NOVA PETZLIFE — Flyway Migration V7: Cryptographically Secure Refresh Tokens
-- Dedicated table for hashed refresh token storage, rotation & reuse detection
-- =============================================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP NULL DEFAULT NULL,
    revoked_by_ip VARCHAR(45) NULL DEFAULT NULL,
    replaced_by_token_hash VARCHAR(64) NULL DEFAULT NULL,
    created_by_ip VARCHAR(45) NULL DEFAULT NULL,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_refresh_token_hash (token_hash),
    INDEX idx_refresh_user_id (user_id),
    INDEX idx_refresh_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
