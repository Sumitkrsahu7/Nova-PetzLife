-- =============================================================================
-- NOVA PETZLIFE — Flyway Migration V5: Feature Enhancements & Granular Permissions
-- =============================================================================

-- 1. Add available_days column to delivery_slots
ALTER TABLE delivery_slots 
ADD COLUMN IF NOT EXISTS available_days VARCHAR(100) NOT NULL DEFAULT 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY';

-- 2. Add user_permissions table for granular staff permissions
CREATE TABLE IF NOT EXISTS user_permissions (
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_up_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_up_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Insert Granular Permissions specified in Part 6
INSERT IGNORE INTO permissions (name, description) VALUES
('ORDER_VIEW', 'View orders, order items, customer details and tracking history'),
('ORDER_EDIT', 'Update order packing/dispatch status, manage items and cancellations'),
('PRODUCT_VIEW', 'View product catalog, pricing, categories and inventory levels'),
('PRODUCT_CREATE', 'Create new products, variants, and attribute sets'),
('PRODUCT_EDIT', 'Edit product details, pricing, stock, images and category mappings'),
('INVENTORY_VIEW', 'View current stock levels, batches, and transaction histories'),
('INVENTORY_EDIT', 'Adjust inventory stock counts, receive batches, and manage low-stock thresholds'),
('CUSTOMER_SUPPORT_VIEW', 'View customer support inquiries, messages, and complaint tickets'),
('CUSTOMER_SUPPORT_EDIT', 'Reply to customer inquiries, resolve support tickets, and update status'),
('STORE_SETTINGS_EDIT', 'Update official store location coordinates, delivery radius, and delivery windows'),
('STAFF_MANAGE', 'Create staff admin accounts, assign granular permissions, and reset credentials');

-- 4. Map all granular permissions to ROLE_SUPER_ADMIN
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.name = 'ROLE_SUPER_ADMIN';

-- 5. Map default staff permissions to ROLE_STAFF_ADMIN
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.name = 'ROLE_STAFF_ADMIN' 
  AND p.name IN ('ORDER_VIEW', 'ORDER_EDIT', 'PRODUCT_VIEW', 'INVENTORY_VIEW', 'CUSTOMER_SUPPORT_VIEW', 'CUSTOMER_SUPPORT_EDIT');

-- 6. Ensure default staff account (staff@novapetzlife.com, id=2) has direct user_permissions assigned
INSERT IGNORE INTO user_permissions (user_id, permission_id)
SELECT 2, p.id FROM permissions p 
WHERE p.name IN ('ORDER_VIEW', 'ORDER_EDIT', 'PRODUCT_VIEW', 'INVENTORY_VIEW', 'CUSTOMER_SUPPORT_VIEW', 'CUSTOMER_SUPPORT_EDIT');

-- 7. Add business WhatsApp and store settings keys to website_settings
INSERT IGNORE INTO website_settings (setting_key, setting_value, description) VALUES
('business_whatsapp_number', '+919876543200', 'Official customer support WhatsApp click-to-chat number'),
('store_contact_phone', '+919876543200', 'Official customer support phone number'),
('store_contact_email', 'support@novapetzlife.com', 'Official customer support email'),
('delivery_radius_km', '20.00', 'Maximum allowable straight-line delivery radius in kilometers');

-- 8. Add cancellation metadata columns to orders if not present
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(255) NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at DATETIME NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(100) NULL;
