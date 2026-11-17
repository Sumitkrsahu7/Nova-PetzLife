# NOVA PETZLIFE — Demo Data & Test Accounts Guide

This document details the development and demonstration test dataset seeded into MySQL 8.4 via Flyway Migration `V4__demo_test_data_seed.sql`.

---

## 1. Seeded Test Accounts

All accounts use the standardized secure password policy: **`Test@123`**.  
Passwords are stored exclusively as 10-round BCrypt hashes in MySQL (`$2a$10$4baJlmea6z3ZOZOeYruwWe6WIrwOVfoLdIOBH/XCiN6bVnWQIbsdS`).  
Both email addresses and 10-digit Indian mobile numbers are supported for login.

| Role | Name | Email Identifier | 10-Digit Mobile | Password | Permissions / Capabilities |
|---|---|---|---|---|---|
| **Super Admin** | Super Admin | `test@gmail.com` | `9876543200` | `Test@123` | Full system access, staff management, shop coordinates, delivery settings, financial & order audits |
| **Staff Admin** | Sujith Kumar | `staff@novapetzlife.com` | `9876543201` | `Test@123` | Order dispatch, inventory adjustment, product updates, delivery OTP verification |
| **Customer 1** | Anand Nair | `anand.nair@example.com` | `9847012345` | `Test@123` | Kowdiar Gardens, Golden Retriever "Rocky" (Adult, 32.5kg) |
| **Customer 2** | Priya Kurup | `priya.kurup@example.com` | `9847123456` | `Test@123` | Heera Heights Pattom, Persian Cat "Bella" (Adult, 4.2kg) |
| **Customer 3** | Arun Menoki | `arun.menoki@example.com` | `9847234567` | `Test@123` | Kuravankonam Ambalamukku Road, Beagle "Max" (Puppy, 12kg) |
| **Customer 4** | Deepa Pillai | `deepa.pillai@example.com` | `9847345678` | `Test@123` | Sasthamangalam Pipamoodu, British Shorthair "Milo" (Kitten, 3.8kg) |
| **Customer 5** | Suresh Kumar | `suresh.kumar@example.com` | `9847456789` | `Test@123` | Kazhakkoottam Technopark Phase 1, Labrador "Leo" (Adult, 35kg) |
| **Customer 6** | Reshma Varma | `reshma.varma@example.com` | `9847567890` | `Test@123` | Ambalamukku Green Valley, Cockatiel "Coco" (Bird) |
| **Customer 7** | Vinod George | `vinod.george@example.com` | `9847678901` | `Test@123` | Murinjapalam Cosmo Hospital, Return request testing |
| **Customer 8** | Anjali Menon | `anjali.menon@example.com` | `9847789012` | `Test@123` | Vellayambalam Jawahar Nagar, Refunded order testing |
| **Customer 9** | Karthik Sreedhar | `karthik.s@example.com` | `9847890123` | `Test@123` | Palayam MG Road, Active customer profile |
| **Customer 10** | Meera Nambiar | `meera.nambiar@example.com` | `9847901234` | `Test@123` | Peroorkada Junction, Active customer profile |

---

## 2. Seeded Catalogue & Inventory Summary

- **Brands (10 Brands):**
  - Royal Canin, Pedigree, Whiskas, Drools, Arden Grange, Farmina N&D, Orijen, Himalaya Pet, Kong, Catit.
- **Categories (16 Hierarchical Categories):**
  - Dogs (Dog Food: Dry Kibble, Wet Gravy, Puppy Formula; Dog Treats & Chews; Dog Grooming; Dog Toys & Accessories)
  - Cats (Cat Food: Dry Kibble, Wet Gravy & Jelly; Cat Litter & Accessories; Cat Toys & Scratchers)
  - Birds & Aquatics (Fish Food Pellets & Supplements; Bird Minerals & Cuttlebone)
- **Products (45 Commercial SKUs):**
  - Complete with realistic INR base prices, discounted prices, GST rates (18% for Food/Grooming, 12% for Collars/Leashes), pet type, life stage, high-resolution photography URLs.
- **Inventory Balance:**
  - **Healthy Stock (39 Products):** 20 to 50 units in stock.
  - **Low Stock (< 5 threshold, 4 Products):**
    - `PED-ADULT-CHK-3KG` (Pedigree Adult 3kg): 3 units available.
    - `DRL-SAUS-100G` (Drools Chicken Sausage): 2 units available.
    - `KONG-EXTR-XL` (Kong Extreme Chew XL): 3 units available.
    - `DRL-FISH-330G` (Drools Fish Pellets): 4 units available.
  - **Out of Stock (2 Products):**
    - `RC-WET-MAXI-140G` (Royal Canin Maxi Gravy): 0 units available.
    - `WHIS-WET-CHK-85G` (Whiskas Chicken Gravy): 0 units available.

---

## 3. Seeded Orders (Full Lifecycle Coverage)

All customer delivery addresses are located within the strict 20 km straight-line radius of the Petzlife store (Kuravankonam Junction, `8.528448, 76.953268`).

| Order Number | Customer | Lifecycle Status | Amount | Payment | Delivery OTP State |
|---|---|---|---|---|---|
| `ORD-DEMO-1001` | Anand Nair | `DELIVERED` | ₹2,984.00 | PAID (Razorpay UPI) | Verified (`is_otp_verified = TRUE`) |
| `ORD-DEMO-1002` | Priya Kurup | `OUT_FOR_DELIVERY` | ₹1,881.00 | PAID (Razorpay Card) | Active OTP hash set; ready for OTP verification modal in admin |
| `ORD-DEMO-1003` | Arun Menoki | `PROCESSING` | ₹1,323.00 | PAID (Razorpay UPI) | Packed and ready for dispatch assignment |
| `ORD-DEMO-1004` | Deepa Pillai | `CONFIRMED` | ₹865.00 | PAID (Razorpay Netbanking) | Confirmed by store manager |
| `ORD-DEMO-1005` | Suresh Kumar | `PENDING_PAYMENT` | ₹1,755.00 | PENDING | Payment initiated, awaiting callback |
| `ORD-DEMO-1006` | Reshma Varma | `CANCELLED` | ₹1,665.00 | REFUNDED | Cancelled before dispatch upon customer request |
| `ORD-DEMO-1007` | Vinod George | `RETURN_REQUESTED` | ₹1,125.00 | PAID | Return request `RET-2026-001` pending inspection |
| `ORD-DEMO-1008` | Anjali Menon | `REFUNDED` | ₹1,485.00 | REFUNDED | Return request `RET-2026-002` approved & refund processed (`rfnd_demo_1008_rzp`) |

---

## 4. Coupons, Reviews & Other Modules

- **Coupons:**
  - `WELCOME50`: Flat ₹50 off on orders >= ₹500
  - `KURAVANKONAM100`: Flat ₹100 off on local orders >= ₹1,000
  - `PETPARENT10`: 10% discount up to ₹250 on orders >= ₹750
- **Customer Reviews:**
  - 6 verified reviews with 4-star and 5-star ratings, realistic comments from Trivandrum pet parents, and store manager replies.
- **Support Tickets:**
  - `TKT-2026-001` (RESOLVED): Subscription enquiry.
  - `TKT-2026-002` (IN_PROGRESS): Damaged item report for order `ORD-DEMO-1007`.
- **Audit Logs:**
  - Initial system audit log entries for order transitions, price changes, and inventory adjustments.

---

## 5. Execution, Verification & Reset Instructions

### Automatic Execution
In development and testing environments, Flyway automatically discovers and applies `V4__demo_test_data_seed.sql` on startup.

### Manual Verification Query (MySQL CLI)
```sql
SELECT 'Users' AS entity, COUNT(*) AS count FROM users
UNION ALL SELECT 'Customers', COUNT(*) FROM customers
UNION ALL SELECT 'Brands', COUNT(*) FROM brands
UNION ALL SELECT 'Categories', COUNT(*) FROM categories
UNION ALL SELECT 'Products', COUNT(*) FROM products
UNION ALL SELECT 'Inventory', COUNT(*) FROM inventory
UNION ALL SELECT 'Orders', COUNT(*) FROM orders
UNION ALL SELECT 'Order Items', COUNT(*) FROM order_items
UNION ALL SELECT 'Payments', COUNT(*) FROM payments
UNION ALL SELECT 'Coupons', COUNT(*) FROM coupons
UNION ALL SELECT 'Reviews', COUNT(*) FROM reviews;
```

Expected baseline counts:
- Users: 12
- Customers: 10
- Brands: 10
- Categories: 16
- Products: 45
- Inventory: 45
- Orders: 8
- Order Items: 11
- Payments: 8
- Coupons: 3
- Reviews: 6

### Safe Demo Reset
To perform a clean development reset:
```bash
# 1. Stop backend service
# 2. Re-run Flyway clean & migrate (development profile only)
mvn -f backend/pom.xml flyway:clean flyway:migrate -Dflyway.cleanDisabled=false
```

### Production Safety
In production (`application-prod.yml`), demo data is never automatically re-seeded. The database contains only baseline system configuration (`V1`, `V2`, `V3`). Flyway migrations use `INSERT IGNORE` to guarantee strict idempotence without risk of overwriting real production records.
