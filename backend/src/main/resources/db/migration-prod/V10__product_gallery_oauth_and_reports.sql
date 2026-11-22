-- V10__product_gallery_oauth_and_reports.sql
-- 1. Support multiple product images with view types (FRONT, BACK, ADDITIONAL)
-- 2. Support Google OAuth identity association and nullable local password hash
-- 3. Make customer phone nullable for external OAuth profiles

ALTER TABLE product_images ADD COLUMN view_type VARCHAR(50) NULL DEFAULT 'ADDITIONAL';

UPDATE product_images SET view_type = 'FRONT' WHERE display_order = 0 OR is_primary = 1;
UPDATE product_images SET view_type = 'BACK' WHERE display_order = 1 AND is_primary = 0;

ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(255) NULL;

ALTER TABLE customers MODIFY COLUMN phone VARCHAR(20) NULL;

CREATE TABLE IF NOT EXISTS user_oauth_identities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_oauth_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT fk_user_oauth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_oauth_user_id (user_id),
    INDEX idx_user_oauth_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
