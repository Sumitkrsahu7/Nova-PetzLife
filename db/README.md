# NOVA PETZLIFE — Database Architecture & Migrations

This directory contains the MySQL 8.x database scripts, Flyway migrations, seeds, and schema documentation for **NOVA PETZLIFE**.

## Directory Structure

```text
db/
├── migrations/         # Canonical Flyway SQL migration scripts
│   ├── V1__initial_schema.sql
│   ├── V2__seed_baseline_data.sql
│   └── V3__google_reviews_and_media.sql
├── seeds/              # Initial seed scripts (roles, permissions, shop location)
│   └── 01_initial_seed.sql
├── docs/               # Schema specifications and entity diagrams
│   └── DATABASE_SCHEMA.md
└── README.md           # This file
```

## Database Rules & Principles

1. **Monolithic Unified Database**: Both customer frontend and admin application connect through the single Spring Boot backend to one MySQL database.
2. **Flyway Managed**: Schema changes must be recorded as versioned migrations (`V1__...sql`, `V2__...sql`, `V3__...sql`) and mirrored in `backend/src/main/resources/db/migration/`.
3. **Financial Precision**: All monetary fields use MySQL `DECIMAL(12,2)`. Floating-point types are strictly forbidden for pricing and transactions.
4. **Auditability**: Order pricing and return/refund policies are stored as immutable snapshots in `order_items` at checkout time.
5. **Kuravankonam Shop Location & 20 km Radius**: Shop location table stores coordinates and delivery radius (20 km default). Delivery calculation requires `is_verified = TRUE` approved by the owner.
6. **Explicit Exclusions**: No veterinary or doctor consultation tables exist in this schema.

## Running Migrations

Flyway runs automatically when the Spring Boot backend starts. To inspect or run migrations manually:

```bash
# Using Flyway CLI (if installed)
flyway -url=jdbc:mysql://localhost:3306/nova_petzlife -user=root -password=root migrate
```
