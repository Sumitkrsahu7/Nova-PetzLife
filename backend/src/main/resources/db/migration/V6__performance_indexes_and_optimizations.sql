-- =============================================================================
-- NOVA PETZLIFE — Flyway Migration V6: Performance Indexes & Query Optimizations
-- Scalability for 300 to 1,000+ daily concurrent shoppers and admin pipelines
-- =============================================================================

-- 1. Composite index for faceted category, stock status & price range filtering
CREATE INDEX idx_products_cat_stock_price ON products (category_id, is_active, base_price);

-- 2. Live orders sorting index for order pipeline & dashboard rendering
CREATE INDEX idx_orders_status_created ON orders (order_status, created_at);

-- 3. Low stock threshold lookup index for automated inventory alerts
CREATE INDEX idx_inventory_stock_threshold ON inventory (available_stock);

-- 4. Fast brand catalog lookup index with active status
CREATE INDEX idx_products_brand_active ON products (brand_id, is_active);
