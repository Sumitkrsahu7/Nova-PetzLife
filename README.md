# NOVA PETZLIFE — Production E-Commerce Platform

A production-grade, enterprise-ready, multi-channel e-commerce platform engineered specifically for **NOVA PETZLIFE**, located at:
> **Petzlife, Kuravankonam Junction, Kuravankonam Ambalamukku Road, Thiruvananthapuram, Kerala 695003, India**  
> **Store Coordinates:** `8.528448, 76.953268`  
> **Strict Delivery Radius:** 20.0 km straight-line (Haversine formula verified)

---

## 1. Architectural Overview & Repository Structure

The repository root strictly maintains four primary operational directories plus production orchestration:

```text
NOVA-PETZLIFE/
├── frontend/             # Customer-Facing Storefront (Angular 17.3, Port 4200)
├── admin/                # Back-Office Admin Management Portal (Angular 17.3, Port 4201)
├── backend/              # Enterprise REST API (Java 21, Spring Boot 3.3.5, Port 8080)
├── db/                   # MySQL 8.x Flyway Migrations (V1, V2, V3) & Seeds
├── docker-compose.yml    # Root Multi-Container Production Orchestrator
├── .env.example          # Environment Configuration Template (Zero Secrets Committed)
└── README.md             # Production Architecture, Setup & Operational Manual
```

### High-Level System Flow

```text
CUSTOMERS (Port 4200)              STORE STAFF / ADMINS (Port 4201)
        │                                         │
        ▼                                         ▼
Customer Angular Storefront               Admin Management Portal
 [Nginx Reverse Proxy Container]           [Nginx Reverse Proxy Container]
        │                                         │
        └───────────────────┬─────────────────────┘
                            │ REST API (/api/v1)
                            ▼
               Spring Boot 3.3.5 Backend (Port 8080)
             [Eclipse Temurin 21 Container, JVM Tuned]
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
MySQL 8.4 LTS Database            External Integrations
[Flyway V1, V2, V3 Migrations]     - Razorpay Official SDK Popup
                                  - 2-Factor Delivery OTP Engine
                                  - Google Business Profile & Places API
```

---

## 2. Core Business Principles & Invariants

1. **Strict 20 km Straight-Line Delivery Radius:**
   - Geofenced exactly at 20.00 km from the Kuravankonam Junction store (`8.528448, 76.953268`).
   - Haversine calculations strictly reject coordinates outside this boundary prior to order placement.
2. **Zero Floating-Point Arithmetic:**
   - All financial and monetary calculations use Java `BigDecimal` (with `RoundingMode.HALF_UP`) and MySQL `DECIMAL(12,2)`.
   - Never use `float` or `double` for currency or taxes.
3. **Zero Medical / Veterinary Boundaries:**
   - The platform strictly sells commercial pet products, premium nutrition, accessories, toys, grooming supplies, and owner-managed grooming appointments.
   - Doctor, prescription, medicine, and veterinary consultation features are strictly prohibited.
4. **Owner-Controlled Policies with Immutable Snapshots:**
   - Return and refund eligibility windows are configured per product by the store owner.
   - At the time of checkout, immutable policy snapshots (`returnPolicySnapshot`, `refundPolicySnapshot`) are stored directly on the `order_items` record.
5. **Indian E-Commerce Compliance:**
   - GSTIN invoices (`32ABCDE1234F1Z5`), HSN codes for goods, SAC code `996813` for local delivery, split CGST + SGST for intra-state Kerala delivery, verified buyer reviews, and 2-factor OTP delivery verification.
6. **Immutable Audit Trail:**
   - Every administrative status transition, price adjustment, inventory change, and review moderation is captured in `audit_logs` with actor details, timestamps, IP address, and before/after state snapshots.

---

## 3. Technology Stack

| Component | Technology | Version / Configuration | Port |
|---|---|---|---|
| **Customer Storefront** | Angular, TypeScript, SCSS | 17.3.0, Standalone Components, Nginx Alpine | `4200` |
| **Admin Portal** | Angular, TypeScript, SCSS | 17.3.0, Standalone Components, Nginx Alpine | `4201` |
| **Backend REST API** | Java, Spring Boot | Java 21 LTS, Spring Boot 3.3.5, Spring Security, Spring Data JPA | `8080` |
| **Database** | MySQL | 8.4 LTS with Flyway Migrations | `3306` |
| **Container Engine** | Docker & Docker Compose | Multi-stage Dockerfiles with non-root security | - |

---

## 4. Production Deployment with Docker Compose

### Prerequisites
- Docker Engine 24+ and Docker Compose v2+ installed.
- Open ports: `4200`, `4201`, `8080`, `5432`.

### One-Command Deployment

```bash
# 1. Clone the repository and enter project root
cd "Nova Petzlife"

# 2. Configure environment variables
cp .env.example .env
# Edit .env with production credentials (JWT secrets, DB passwords, Razorpay keys)

# 3. Build and launch all production containers in detached mode
docker-compose up --build -d

# 4. Verify running container health
docker-compose ps
```

### Accessing the Live Services

| Service | URL | Default Credentials / Notes |
|---|---|---|
| **Customer Storefront** | `http://localhost:4200` | Public customer catalog, cart, checkout, tracking |
| **Admin Management Portal** | `http://localhost:4201` | Staff & Super Admin operational management |
| **Backend REST API** | `http://localhost:8080` | Core API |
| **API Health Check** | `http://localhost:8080/api/v1/health` | HTTP 200 OK |
| **Swagger UI Documentation** | `http://localhost:8080/swagger-ui.html` | Interactive OpenAPI 3.0 specification |
| **OpenAPI Schema** | `http://localhost:8080/v3/api-docs` | Raw OpenAPI JSON schema |
| **MySQL 8.x Database** | `localhost:3306` | Database `nova_petzlife` |

---

## 5. Local Development Setup (Manual)

### A. Spring Boot Backend (Port 8080)
```bash
cd backend

# Copy environment configuration
cp .env.example .env

# Run full integration test suite (57 tests)
mvn test "-Dspring.profiles.active=test"

# Package into production JAR
mvn clean package -DskipTests

# Run with local MySQL
java -jar target/backend-0.0.1-SNAPSHOT.jar

# Or run with in-memory H2 test profile
java "-Dspring.profiles.active=test" -jar target/backend-0.0.1-SNAPSHOT.jar
```

### B. Customer Frontend (Port 4200)
```bash
cd frontend

# Install dependencies
npm install

# Run development server
npm start

# Build production bundle
npm run build
```

### C. Admin Application (Port 4201)
```bash
cd admin

# Install dependencies
npm install

# Run development server
npm start

# Build production bundle
npm run build
```

---

## 6. Database Schema & Flyway Migration Plan

Database migrations reside in `db/migrations/` and `backend/src/main/resources/db/migration/`:

1. **`V1__initial_schema.sql`** (19 Core Tables):
   - `stores`, `store_locations`, `operating_hours`, `categories`, `brands`, `products`, `product_variants`, `inventory`, `inventory_transactions`, `users`, `roles`, `user_roles`, `user_addresses`, `orders`, `order_items`, `delivery_slots`, `delivery_assignments`, `payments`, `audit_logs`.
2. **`V2__promotions_and_pet_profiles.sql`** (6 Feature Tables):
   - `promotions`, `coupon_redemptions`, `pet_profiles`, `notifications`, `support_tickets`, `support_messages`.
3. **`V3__google_reviews_and_media.sql`** (4 Media & Social Tables):
   - `reviews`, `review_images`, `google_reviews`, `product_images`.

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
- **Docker Security:**
  - All containers execute as unprivileged non-root users (`novapetzlife` / `nginx`).
  - Read-only container root filesystems where applicable.
  - JVM parameters tuned for container resource constraints: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`.

---

## 8. Verification & Test Suite Summary

The backend includes 57 comprehensive automated integration tests covering the entire customer and administrative lifecycle:
- `BackendApplicationTests`: Context boot verification.
- `StoreAndDeliveryIntegrationTest`: Kuravankonam 20 km boundary enforcement, Haversine geo-distance calculation.
- `CatalogAndInventoryIntegrationTest`: Hierarchical categories, brand isolation, stock tracking.
- `CartAndCheckoutIntegrationTest`: Tax calculations (GST), delivery fee rules, checkout pipeline.
- `PaymentAndInvoiceIntegrationTest`: Razorpay signature verification, GST invoice generation (`32ABCDE1234F1Z5`).
- `OrderLifecycleAndFulfillmentIntegrationTest`: Order transitions, OTP delivery verification, stock decrements.
- `ReturnAndRefundIntegrationTest`: Return windows, policy snapshots, refund calculations.
- `PromotionAndPetProfileIntegrationTest`: Coupons, discounts, pet profiles, life-stage pet food recommendations.
- `NotificationAndAnalyticsIntegrationTest`: Multi-channel SMS/Email dispatch, sales analytics reporting.
- `ReviewAndGoogleBusinessIntegrationTest`: Product ratings, verified buyer badges, Google Business Profile live sync.
- `AuditAndSecurityIntegrationTest`: Audit logging engine, HTTP security headers, admin query RBAC.

---

© 2026 Nova Petzlife. All Rights Reserved. Engineered for excellence in Thiruvananthapuram, Kerala.
