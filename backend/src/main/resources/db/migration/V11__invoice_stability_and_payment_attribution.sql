-- V11__invoice_stability_and_payment_attribution.sql
-- 1. Invoices: support historical document classification (PROVISIONAL_RECEIPT vs GST_TAX_INVOICE) and snapshot discount
-- 2. Orders: add paid_at timestamp for authoritative financial payment-date attribution
-- 3. Payments: add completed_at timestamp for payment gateway verification/capture completion

ALTER TABLE invoices ADD COLUMN document_type VARCHAR(50) NOT NULL DEFAULT 'PROVISIONAL_RECEIPT';
ALTER TABLE invoices ADD COLUMN discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;

ALTER TABLE orders ADD COLUMN paid_at DATETIME NULL;
CREATE INDEX idx_orders_paid_at ON orders (paid_at);

ALTER TABLE payments ADD COLUMN completed_at DATETIME NULL;
CREATE INDEX idx_payments_completed_at ON payments (completed_at);
