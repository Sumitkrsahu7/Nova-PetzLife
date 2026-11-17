# NOVA PETZLIFE — Database Entity Documentation

## Core Tables Summary

| Module | Table Name | Purpose |
|---|---|---|
| **Auth & Users** | `roles`, `permissions`, `role_permissions`, `users`, `user_roles` | RBAC for super admins, staff, and customers |
| **Customers** | `customers`, `customer_addresses`, `pet_profiles` | Customer data, addresses, and pet profiles |
| **Catalog** | `brands`, `categories`, `products`, `product_images`, `product_variants`, `product_attributes` | Hierarchical catalog, brand isolation, SKU/variants |
| **Inventory** | `inventory`, `inventory_batches`, `inventory_transactions` | Atomic stock levels, reservations, batch tracking, expiry |
| **Cart** | `carts`, `cart_items` | Active shopping carts and guest session tracking |
| **Orders** | `orders`, `order_items`, `order_status_history` | Orders, immutable price/policy snapshots, lifecycle history |
| **Payments** | `payments`, `refunds` | Razorpay transactions, signatures, status tracking |
| **Returns** | `product_return_policies`, `return_requests`, `return_request_items`, `return_inspections` | Owner-controlled return/refund policies and inspections |
| **Promotions** | `coupons`, `coupon_usage`, `loyalty_accounts`, `loyalty_transactions` | Coupons, usage limits, optional reward points |
| **Delivery** | `shop_location`, `delivery_settings`, `delivery_slots`, `delivery_bookings` | Kuravankonam coordinates, 20 km radius, delivery slots |
| **Tax** | `tax_configurations`, `product_tax_configurations`, `invoices` | Indian GST rates (CGST/SGST/IGST), HSN codes, invoices |
| **Reviews** | `reviews`, `review_images` | Website product reviews (strictly separate from Google reviews) |
| **Support** | `support_tickets`, `support_messages` | Commercial customer support |
| **System** | `website_settings`, `audit_logs`, `notifications` | Configurable parameters, security audit trail, notifications |
