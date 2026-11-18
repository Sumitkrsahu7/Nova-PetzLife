-- =============================================================================
-- NOVA PETZLIFE — Flyway Migration V2: Baseline System Data
-- Shop Location: Petzlife, Kuravankonam Junction, Thiruvananthapuram, Kerala
-- =============================================================================

-- 1. Default Roles
INSERT INTO roles (name, description) VALUES
('ROLE_SUPER_ADMIN', 'Super Administrator / Shop Owner with full system access'),
('ROLE_STAFF_ADMIN', 'Staff Administrator with restricted operational access'),
('ROLE_CUSTOMER', 'Standard customer account');

-- 2. Default Permissions
INSERT INTO permissions (name, description) VALUES
('MANAGE_PRODUCTS', 'Create, update, and manage products and variants'),
('MANAGE_CATEGORIES', 'Create, update, and reorder categories'),
('MANAGE_BRANDS', 'Manage brand catalog'),
('MANAGE_INVENTORY', 'Manage stock, batches, and adjustments'),
('MANAGE_ORDERS', 'Process and update orders and dispatch tasks'),
('MANAGE_DELIVERY', 'Configure shop coordinates and delivery radius/slots'),
('MANAGE_RETURNS', 'Review and process return and refund requests'),
('MANAGE_PRICING', 'Update prices, margins, and discounts'),
('MANAGE_TAX', 'Configure Indian GST rates and HSN codes'),
('VIEW_ANALYTICS', 'Access sales and financial analytics');

-- 3. Map All Permissions to ROLE_SUPER_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ROLE_SUPER_ADMIN';

-- 4. Staff Admin Permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.name = 'ROLE_STAFF_ADMIN' 
  AND p.name IN ('MANAGE_PRODUCTS', 'MANAGE_INVENTORY', 'MANAGE_ORDERS');

-- 5. Confirmed Shop Location (Petzlife, Kuravankonam)
INSERT INTO shop_location (
    shop_name,
    address_line,
    city,
    state,
    country,
    pin_code,
    latitude,
    longitude,
    delivery_radius_km,
    is_verified
) VALUES (
    'NOVA PETZLIFE',
    'Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road',
    'Thiruvananthapuram',
    'Kerala',
    'India',
    '695003',
    8.5284480,  -- Kuravankonam Junction reference; requires owner verification
    76.9532680,
    20.00,       -- Strict 20 km straight-line radius
    TRUE         -- Verified and active for 20 km straight-line delivery radius from Kuravankonam
);

-- 6. Default Delivery Settings
INSERT INTO delivery_settings (
    delivery_charge,
    free_delivery_threshold,
    min_order_value,
    prep_time_minutes,
    is_delivery_enabled
) VALUES (
    50.00,
    500.00,
    100.00,
    60,
    TRUE
);

-- 7. Default Delivery Slots
INSERT INTO delivery_slots (slot_name, start_time, end_time, max_capacity, is_active) VALUES
('Morning Slot (09:00 AM - 01:00 PM)', '09:00:00', '13:00:00', 15, TRUE),
('Afternoon Slot (01:00 PM - 05:00 PM)', '13:00:00', '17:00:00', 15, TRUE),
('Evening Slot (05:00 PM - 08:30 PM)', '17:00:00', '20:30:00', 15, TRUE);

-- 8. Default Indian GST Tax Configurations
INSERT INTO tax_configurations (tax_name, hsn_code, cgst_rate, sgst_rate, igst_rate, is_active) VALUES
('Pet Food (Dry & Wet Gravy)', '2309', 9.00, 9.00, 18.00, TRUE),
('Pet Collars, Leashes & Harnesses', '4201', 6.00, 6.00, 12.00, TRUE),
('Pet Grooming Shampoos & Conditioners', '3305', 9.00, 9.00, 18.00, TRUE),
('Pet Feeding Bowls & Plastic Accessories', '3924', 9.00, 9.00, 18.00, TRUE);
