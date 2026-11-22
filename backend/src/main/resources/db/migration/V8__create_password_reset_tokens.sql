-- =============================================================================
-- NOVA PETZLIFE — Flyway Migration V8: Password Reset Tokens
-- Stores hashed, cryptographically secure password reset tokens with expiration
-- =============================================================================

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP NULL DEFAULT NULL,
    used_by_ip VARCHAR(45) NULL DEFAULT NULL,
    created_by_ip VARCHAR(45) NULL DEFAULT NULL,
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_pwd_reset_token_hash (token_hash),
    INDEX idx_pwd_reset_user_id (user_id),
    INDEX idx_pwd_reset_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
