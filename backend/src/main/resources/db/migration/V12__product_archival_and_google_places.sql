-- V12__product_archival_and_google_places.sql
-- 1. Product Lifecycle & Safe Archival: Preserve complete historical transactional records
--    while allowing discontinued products to be excluded from active catalog, search, and purchasing.
-- 2. De-feature placeholder/seeded reviews from public display to uphold customer trust.

ALTER TABLE products ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN archived_at DATETIME NULL;
ALTER TABLE products ADD COLUMN archived_by VARCHAR(100) NULL;

CREATE INDEX idx_products_is_archived ON products (is_archived);

-- De-feature artificial seed reviews from public customer testimonials
UPDATE google_reviews 
SET is_featured = FALSE 
WHERE google_review_id LIKE 'ChIJ_petzlife_rev_%';
