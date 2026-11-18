# NOVA PETZLIFE — Production E-Commerce Platform

A production-grade, enterprise-ready, multi-channel e-commerce platform engineered specifically for **NOVA PETZLIFE**, located at:
> **Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road, Thiruvananthapuram, Kerala 695003, India**  
> **Store Coordinates:** `8.528448, 76.953268`  
> **Delivery Boundary:** 20.0 km radius (High-precision WGS-84 Geodesic & Urban Road Circuity Routing)

---

## 1. Architectural Overview & Repository Structure

The repository maintains dedicated operational directories for full-stack standalone deployment:

```text
NOVA-PETZLIFE/
├── frontend/             # Customer Storefront (Angular 17.3, Port 4200)
├── admin/                # Back-Office Admin Portal (Angular 17.3, Port 4201)
├── backend/              # Enterprise REST API (Java 21, Spring Boot 3.3.5, Port 8080)
├── db/                   # MySQL 8.x Database Initialization & Flyway Migrations
├── .env.example          # Environment Configuration Template (Zero Secrets Committed)
├── DEPLOYMENT.md         # Production Standalone Executable JAR & Nginx Architecture
└── README.md             # Production Architecture, Setup & Operational Manual
```

### High-Level System Flow

```text
CUSTOMERS (Port 4200)                         STORE STAFF / ADMINS (Port 4201)
        │                                                     │
        ▼                                                     ▼
Customer Angular Storefront                           Admin Management Portal
[Nginx Web Server / Static Root]                      [Nginx Web Server / Static Root]
        │                                                     │
        └───────────────────────┬─────────────────────────────┘
                                │ HTTPS Reverse Proxy (/api/v1)
                                ▼
                   Spring Boot 3.3.5 Executable JAR (Port 8080)
                 [Standalone Java 21 LTS Process / Systemd Service]
                                │
                ┌───────────────┴───────────────┐
                ▼                               ▼
     MySQL 8.4 LTS Database           External Integrations
  [Native Database Server, Port 3306]  - Razorpay Official SDK Checkout
  [Flyway Schema Migrations V1-V7]     - 2-Factor Delivery OTP Engine
  [Strict novapetz DB Account]         - Leaflet & OpenStreetMap Routing
                                       - Google Business Profile Sync
```

---

## 2. Core Business Principles & Invariants

1. **20 km Delivery Serviceability & Geodesic Routing:**
   - Geofenced within a 20.00 km radius of the Kuravankonam Junction hub (`8.528448, 76.953268`).
   - Evaluates high-precision geodesic distance (WGS-84 ellipsoid) combined with South Indian urban road circuity modeling (~1.20–1.28x road network detour factor) to provide accurate straight-line bounds and realistic road transit estimates.
2. **Zero Floating-Point Financial Arithmetic:**
   - All monetary amounts, line-item totals, GST breakdowns, delivery fees, and discounts strictly utilize Java `BigDecimal` (with `RoundingMode.HALF_UP`) and MySQL `DECIMAL(12,2)`.
3. **Strict Commercial Pet Scope (No Prescription Medicine):**
   - The platform exclusively retails pet nutrition, food, treats, accessories, toys, hygiene supplies, grooming products, and owner-managed grooming appointments.
   - Prescription pharmaceuticals and unlicensed veterinary advice features are strictly prohibited.
4. **Owner-Controlled Policies with Immutable Checkout Snapshots:**
   - Return and refund eligibility windows are configured per product by the store owner.
   - At checkout, immutable policy snapshots (`returnPolicySnapshot`, `refundPolicySnapshot`) are recorded on `order_items` to protect against retroactively altered policies.
5. **Indian E-Commerce Compliance:**
   - GSTIN invoicing (`32ABCDE1234F1Z5`), HSN classification, SAC code `996813` for local delivery, Kerala intra-state CGST + SGST split, verified buyer review constraints, and 2-factor OTP delivery verification.
6. **Immutable Audit Trail:**
   - Every administrative status transition, price adjustment, inventory movement, and review moderation is logged in `audit_logs` with actor details, timestamps, IP addresses, and state diffs.

---

## 3. Technology Stack

| Component | Technology | Version / Configuration | Default Port |
|---|---|---|---|
| **Customer Storefront** | Angular, TypeScript, SCSS | Angular 17.3.0, Standalone Components | `4200` |
| **Admin Portal** | Angular, TypeScript, SCSS | Angular 17.3.0, Standalone Components | `4201` |
| **Backend REST API** | Java, Spring Boot | Java 21 LTS, Spring Boot 3.3.5, Spring Security, JPA | `8080` |
| **Database** | MySQL | MySQL 8.4 LTS Native Server (Connector/J 8.3+, Flyway MySQL) | `3306` |
| **Reverse Proxy & Web Server** | Nginx | Nginx 1.22+ with TLS 1.3 & HTTP/2 | `80` / `443` |
| **Process Supervision** | Systemd | Linux system service (`nova-petzlife.service`) | - |

> [!NOTE]
> **Zero Docker & Zero PostgreSQL Dependencies:** The project has been fully decoupled from Docker and PostgreSQL. It runs natively as a standard standalone Java 21 executable JAR, native MySQL 8.4+ database, and pre-compiled static Angular bundles served by Nginx.

---

## 4. Production Deployment (Standalone Executable JAR & Nginx)

For full step-by-step production setup, systemd unit definitions, SSL configuration, and backup scripts, see [DEPLOYMENT.md](file:///d:/Projects_StandAlone/Nova%20Petzlife/DEPLOYMENT.md).

### Quick Production Deployment Summary

#### 1. Setup MySQL Database
```bash
# Execute initialization script to create database and application user
mysql -u root -p < db/create_database_and_user.sql
```

#### 2. Build & Package Spring Boot Executable JAR
```bash
cd backend
mvn clean package -DskipTests
# Output: target/backend-0.0.1-SNAPSHOT.jar
```

#### 3. Build Angular Frontends
```bash
# Build Customer Storefront
cd ../frontend
npm install
npm run build
# Output: dist/frontend/browser

# Build Admin Portal
cd ../admin
npm install
npm run build
# Output: dist/admin/browser
```

#### 4. Run the Backend Process
```bash
# With systemd or directly via Java 21:
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/nova_petzlife?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8"
export SPRING_DATASOURCE_USERNAME="novapetz"
export SPRING_DATASOURCE_PASSWORD="your_secure_db_password"
export JWT_SECRET="your_secure_256_bit_random_secret_key"
export RAZORPAY_KEY_ID="your_razorpay_key"
export RAZORPAY_KEY_SECRET="your_razorpay_secret"

java -Xms512m -Xmx1024m -jar target/backend-0.0.1-SNAPSHOT.jar
```

---

## 5. Local Development Setup

### A. Spring Boot Backend (Port 8080)
```bash
cd backend

# Configure local development environment
cp .env.example .env

# Run full integration test suite
mvn test -Dspring.profiles.active=test

# Start backend locally with MySQL
mvn spring-boot:run
```

### B. Customer Frontend (Port 4200)
```bash
cd frontend
npm install
npm start
# Access storefront at: http://localhost:4200
```

### C. Admin Application (Port 4201)
```bash
cd admin
npm install
npm start
# Access admin portal at: http://localhost:4201
```

---

## 6. Database Schema & Flyway Migration Plan

Database migrations reside in `backend/src/main/resources/db/migration/`:

1. **`V1__initial_schema.sql`**: Core e-commerce entities (users, roles, permissions, stores, shop locations, products, variants, inventory, orders, items, payments, delivery slots, audit logs).
2. **`V2__seed_baseline_data.sql`**: Baseline store configuration, permissions, Kuravankonam shop location, and default delivery parameters.
3. **`V3__seed_storefront_catalog.sql`**: Curated product catalog, categories (cat food, dog food, grooming, hygiene, accessories), and initial stock levels.
4. **`V4__seed_google_reviews.sql`**: Physical store Google Reviews for Kuravankonam storefront listing.
5. **`V5__seed_demo_accounts.sql`**: Demonstrative accounts for customer and staff roles with password hashes.
6. **`V6__delivery_management.sql`**: Enhanced delivery slot scheduling, assignments, and coordinates.
7. **`V7__system_hardening.sql`**: Constraints, indexes, and performance optimizations for high concurrency.

---

## 7. Security Hardening & Compliance Specifications

- **HTTP Security Headers:**
  - `X-Frame-Options: DENY` (Clickjacking mitigation)
  - `X-Content-Type-Options: nosniff` (MIME sniffing prevention)
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains` (HSTS enforced over HTTPS)
- **Role-Based Access Control (RBAC):**
  - `ROLE_CUSTOMER`: Access to catalog, cart, checkout, personal orders, reviews, pet profiles.
  - `ROLE_STAFF_ADMIN`: Inventory updates, order fulfillment, delivery assignment, support ticket replies.
  - `ROLE_SUPER_ADMIN`: Store configuration, staff management, audit log inspection, review moderation.
- **Process & Server Security:**
  - Dedicated unprivileged system user (`novapetz`) executes the backend JAR process.
  - MySQL application user is granted strictly required DML/DDL permissions on `nova_petzlife.*` (no administrative `ALL PRIVILEGES`).
  - Production configurations enforce runtime injection of secrets (`JWT_SECRET`, database passwords) with zero plaintext fallbacks in version control.

---

## 8. Verification & Test Suite Summary

The backend integration test suite validates the customer and administrative workflows:
- `BackendApplicationTests`: Context boot verification.
- `DeliveryDistanceAndEligibilityIntegrationTest`: Kuravankonam 20 km boundary enforcement, high-precision geodesic calculation, and road routing estimation.
- `CatalogAndInventoryIntegrationTest`: Hierarchical categories, brand isolation, stock tracking.
- `CartAndCheckoutIntegrationTest`: Tax calculations (GST), delivery fee rules, checkout pipeline.
- `PaymentAndInvoiceIntegrationTest`: Razorpay checkout integration, GST invoice generation (`32ABCDE1234F1Z5`).
- `OrderLifecycleAndFulfillmentIntegrationTest`: Order transitions, OTP delivery verification, stock decrements.
- `ReturnAndRefundIntegrationTest`: Return windows, policy snapshots, refund calculations.
- `PromotionAndPetProfileIntegrationTest`: Coupons, discounts, pet profiles, life-stage pet food recommendations.
- `ReviewAndGoogleBusinessIntegrationTest`: Product ratings, verified buyer badges, Google Business Profile live sync.
- `AuditAndSecurityIntegrationTest`: Audit logging engine, HTTP security headers, admin query RBAC.

---

© 2026 Nova Petzlife. All Rights Reserved. Engineered for excellence in Thiruvananthapuram, Kerala.
