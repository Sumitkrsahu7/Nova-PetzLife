-- =============================================================================
-- NOVA PETZLIFE — Flyway Migration V14: Production Demo Data Sanitization
-- Purges active demo credentials, deactivates demo users, archives demo catalog,
-- and preserves complete relational integrity, audit logs, and V5 user_id=2 FKs.
-- =============================================================================

-- 1. Deactivate demo administrator accounts (test@gmail.com and staff@novapetzlife.com)
-- Scramble password hashes to unmatchable strings so credentials cannot authenticate.
-- Retain user records (especially user_id = 2) so foreign keys in user_permissions remain valid.
UPDATE users 
SET is_active = FALSE,
    password_hash = '$2a$10$DEACTIVATED_DEMO_ACCOUNT_CANNOT_AUTHENTICATE_000000000000',
    updated_at = NOW()
WHERE email IN ('test@gmail.com', 'staff@novapetzlife.com')
   OR id IN (1, 2);

-- 2. Deactivate demo customer accounts (anand.nair@example.com etc., IDs 3 through 12)
UPDATE users 
SET is_active = FALSE,
    password_hash = '$2a$10$DEACTIVATED_DEMO_ACCOUNT_CANNOT_AUTHENTICATE_000000000000',
    updated_at = NOW()
WHERE email LIKE '%@example.com'
   OR (id >= 3 AND id <= 12 AND email LIKE '%example.com%');

-- 3. Invalidate all active refresh tokens and password reset tokens for deactivated demo users
DELETE FROM refresh_tokens 
WHERE user_id IN (1, 2) 
   OR user_id IN (SELECT id FROM users WHERE is_active = FALSE);

DELETE FROM password_reset_tokens 
WHERE user_id IN (1, 2) 
   OR user_id IN (SELECT id FROM users WHERE is_active = FALSE);

-- 4. Archive demo products (IDs 1 through 40) seeded in V4
-- Deactivate and archive so they are excluded from customer catalog, search, and purchasing,
-- while preserving complete transactional history for any historical orders.
UPDATE products 
SET is_archived = TRUE,
    is_active = FALSE,
    archived_at = NOW(),
    archived_by = 'V14_DEMO_DATA_SANITIZATION'
WHERE id <= 40
  AND (sku LIKE 'RC-%' OR sku LIKE 'PED-%' OR sku LIKE 'WHIS-%' OR sku LIKE 'DRL-%'
       OR sku LIKE 'AG-%' OR sku LIKE 'FAR-%' OR sku LIKE 'ORJ-%' OR sku LIKE 'HIM-%'
       OR sku LIKE 'KNG-%' OR sku LIKE 'CAT-%' OR sku LIKE 'SHB-%');

-- 5. Mark demo customer reviews as ARCHIVED to prevent unverified customer testimonials
UPDATE reviews 
SET status = 'ARCHIVED',
    updated_at = NOW()
WHERE id <= 6;

-- 6. Deactivate demo promotional coupons seeded in V4
UPDATE coupons 
SET is_active = FALSE
WHERE code IN ('FIRST50', 'NOVA100', 'PETLOVE10');

-- 7. Archive demo orders seeded in V4 with explicit audit reason
UPDATE orders 
SET cancellation_reason = 'DEMO_DATA_ARCHIVED',
    cancelled_at = NOW(),
    cancelled_by = 'V14_DEMO_DATA_SANITIZATION'
WHERE id <= 4 
  AND order_number LIKE 'ORD-20261113-%';
