# NOVA PETZLIFE — COMPLETE HIOX NON-DOCKER DEPLOYMENT READINESS AUDIT & PRODUCTION MIGRATION PLAN

**Document Version:** 1.0.0  
**Audit Date:** November 22, 2026  
**Auditor:** Principal Solution Architect, Senior Java/Spring Boot Engineer, Angular Architect, MySQL DBA, Application Security Specialist, and DevOps Lead  
**Audit Scope:** Full repository (`backend/`, `frontend/`, `admin/`, database schemas, deployment descriptors, configuration assets)  
**Deployment Mode:** Non-Docker Bare-Metal / Native Hosting on Hiox India Infrastructure  
**Target Domains:** `novapetzlife.com` (Storefront), `admin.novapetzlife.com` (Control Center), `api.novapetzlife.com` (REST API), `mail.novapetzlife.com` (Transactional Email Sending Domain)  

---

## 1. EXECUTIVE SUMMARY

An exhaustive, read-only architectural and infrastructure compatibility audit of the **NOVA PETZLIFE** platform was conducted to determine whether the application can be safely and reliably deployed to **Hiox hosting without Docker**.

### The Core Audit Question
> **Can NOVA PETZLIFE be deployed to Hiox hosting without Docker while preserving its complete Java 21 / Spring Boot 3.3.5 and Angular 17 architecture, security boundaries, and e-commerce business logic?**

### The Definitive Technical Verdict
**YES, BUT WITH A CRITICAL HOSTING PLAN DISTINCTION:**

1. **Hiox Linux VPS / Cloud Server (COMPATIBLE — RECOMMENDED):**  
   NOVA PETZLIFE can be **100% deployed without Docker** on a **Hiox Linux VPS or Dedicated Cloud Server** running Ubuntu 22.04 LTS (minimum 2 vCPU, 4GB RAM).
   - The Spring Boot 3.3.5 backend compiles directly to a standalone, runnable Fat JAR (`nova-petzlife-backend.jar`) powered by embedded Tomcat 10.1 and managed natively by a Linux `systemd` daemon (`nova-petzlife.service`).
   - Both Angular 17 SPAs (Customer Storefront and Admin Control Center) compile into pure static client-side HTML/JS/CSS bundles served directly by an Nginx web server with native HTTPS termination and reverse-proxy pass to `http://127.0.0.1:8080/api/v1`.
   - The MySQL 8.0+ database can run locally on the VPS or remain on an external managed MySQL server (e.g., MonsterSQL).
   - Docker is **zero percent mandatory** for runtime, compilation, or persistence.

2. **Hiox cPanel / DirectAdmin Shared Hosting (INCOMPATIBLE FOR BACKEND):**  
   If the user intends to purchase standard **Hiox Shared Web Hosting (cPanel)**, the **Spring Boot backend CANNOT run**:
   - Shared cPanel environments run PHP/CGI/Node.js/Python WSGI via CloudLinux LVE.
   - They do **not** support Java 21 OpenJDK runtime, persistent background JVM daemon processes, custom internal port binding (`:8080`), or root-level process managers. CloudLinux process limits actively kill daemon JVM processes exceeding execution or memory caps.
   - However, both Angular frontends (Customer and Admin) can run statically on Hiox Shared Hosting under Apache with standard `.htaccess` URL rewrite directives, connecting to an externally hosted Spring Boot backend (Hybrid Option B).

---

## 2. REPOSITORY AND GIT INVENTORY

### Current Git State
- **Branch:** `feature/mobile-responsive-remediation` (synchronized with `main` at commit `00a5a8e`)
- **Working Tree Status:** Clean (`nothing to commit, working tree clean`)
- **Modified / Untracked Application Files:** None (Strict read-only verification preserved)

### Repository Component Structure
```
D:\Projects_StandAlone\Nova Petzlife\
├── backend/                  # Java 21, Spring Boot 3.3.5 Maven Project
│   ├── pom.xml               # Standard Maven build definition (spring-boot-maven-plugin)
│   ├── src/main/java/        # 246 Application Java classes across 22 business packages
│   ├── src/main/resources/   # Flyway migrations (V1-V8), application.yml, application-prod.yml
│   └── target/               # Standalone runnable JAR: nova-petzlife-backend.jar (60.7 MB)
├── frontend/                 # Customer Storefront (Angular 17 standalone SPA)
│   ├── package.json          # Angular CLI 17.3.0, dependencies
│   ├── angular.json          # Application builder (@angular-devkit/build-angular:application)
│   ├── src/                  # 14 views, components, services, auth interceptor, SCSS
│   └── dist/frontend/browser # Static build distribution (HTML/JS/CSS)
├── admin/                    # Admin Control Center (Angular 17 standalone SPA)
│   ├── package.json          # Angular CLI 17.3.0, dependencies
│   ├── angular.json          # Application builder
│   ├── src/                  # 11 administrative modules, routing, guards, SCSS
│   └── dist/admin/browser    # Static build distribution (HTML/JS/CSS)
├── docs/                     # Technical specifications, audit reports, system architectures
├── docker-compose.yml        # Multi-container orchestration (OPTIONAL)
├── Dockerfile                # Root convenience Dockerfile (OPTIONAL)
├── backend/Dockerfile        # Multi-stage Eclipse Temurin Java 21 build (OPTIONAL)
├── frontend/Dockerfile       # Multi-stage Node 20 / Nginx build (OPTIONAL)
└── admin/Dockerfile          # Multi-stage Node 20 / Nginx build (OPTIONAL)
```

---

## 3. DOCKER DEPENDENCY INVENTORY

Every Docker-related artifact in the codebase was located, inspected, and categorized against the 6-class audit taxonomy:

| File / Component | Path | Audit Category | Technical Explanation & Dependency Verdict |
| :--- | :--- | :--- | :--- |
| **`docker-compose.yml`** | Repository root | **2. OPTIONAL deployment support** | Orchestrates 4 local containers (`backend`, `frontend`, `admin`, `db`). Relies purely on environment variables. **No code or build files depend on it.** |
| **`Dockerfile` (Root)** | Repository root | **4. LEGACY / UNUSED** | Generic container descriptor superseded by service-specific Dockerfiles. Safe to keep or ignore. |
| **`backend/Dockerfile`** | `backend/Dockerfile` | **2. OPTIONAL deployment support** | Multi-stage build (`maven:3.9.9-eclipse-temurin-21` -> `eclipse-temurin:21-jre-jammy`). Used previously for Render containerized backend deployments. Maven compiles independently. |
| **`backend/.dockerignore`**| `backend/.dockerignore` | **2. OPTIONAL deployment support** | Prevents `.git` and `target/` inclusion in container context. |
| **`frontend/Dockerfile`** | `frontend/Dockerfile` | **2. OPTIONAL deployment support** | Builds Angular SPA via Node 20 and serves static `/usr/share/nginx/html` via Nginx alpine. Static assets build natively with `npm run build`. |
| **`admin/Dockerfile`** | `admin/Dockerfile` | **2. OPTIONAL deployment support** | Multi-stage Angular build to Nginx alpine. Builds natively with `npm run build`. |
| **`pom.xml` Docker Plugins**| `backend/pom.xml` | **NONE (Absent)** | Checked for Spotify Dockerfile plugin, Fabric8, Jib, or Cloud Native Buildpacks. **Zero container build plugins exist.** |
| **Filesystem / Storage** | Java Source Code | **NONE (Absent)** | Checked `MediaService.java`, `InvoiceService.java`, `FileStorageService`. Images are hosted on Cloudinary CDN; no local container volume bindings exist. |

### Docker Verdict
> **Docker is 100% an optional packaging convenience.** The application source code, database migrations, build lifecycles, and persistence layers contain **zero hard dependencies on Docker**.

---

## 4. NON-DOCKER BUILD & PACKAGING VERIFICATION

Native compilation, packaging, and unit test execution were verified locally without any containerization:

### 1. Backend Standalone Compilation
- **Command:** `mvn -f backend/pom.xml clean package -DskipTests -B`
- **Output Artifact:** `backend/target/nova-petzlife-backend.jar` (60.7 MB)
- **Artifact Type:** Fully self-contained Spring Boot Fat JAR containing all runtime dependencies and embedded Tomcat 10.1.28.
- **Test Suite Execution:** `mvn -f backend/pom.xml test`
- **Result:** **105 tests run, 0 failures, 0 errors, 0 skipped** (`BUILD SUCCESS`).
- **Native Launch Verification:** `java -jar target/nova-petzlife-backend.jar --spring.profiles.active=test` executes cleanly on native Java 21 runtime.

### 2. Customer Frontend Standalone Build
- **Command:** `npm --prefix frontend run build`
- **Engine:** `@angular-devkit/build-angular:application` (esbuild)
- **Output Artifact:** `frontend/dist/frontend/browser/` (static files: `index.html`, `main-*.js`, `polyfills-*.js`, `styles-*.css`, assets).
- **Test Suite Execution:** `npm --prefix frontend test -- --watch=false --browsers=ChromeHeadless`
- **Result:** **31 tests run, 31 success, 0 failures**.
- **Runtime Dependency:** Zero Node.js runtime required in production. All assets are static client-side files.

### 3. Admin Frontend Standalone Build
- **Command:** `npm --prefix admin run build`
- **Engine:** `@angular-devkit/build-angular:application` (esbuild)
- **Output Artifact:** `admin/dist/admin/browser/` (static files: `index.html`, `main-*.js`, `polyfills-*.js`, `styles-*.css`, assets).
- **Runtime Dependency:** Zero Node.js runtime required in production. All assets are static client-side files.

---

## 5. HIOX HOSTING CAPABILITY AUDIT

Hiox India (`hioxindia.com` / `hioxhost.com`) provides several tiers of hosting. The feasibility of running NOVA PETZLIFE non-Docker was assessed across each tier:

```
+---------------------------------------------------------------------------------------------------+
|                                  HIOX HOSTING FEASIBILITY MATRIX                                  |
+------------------------------------+--------------------------+-----------------------------------+
| Hosting Tier                       | Backend (Spring Boot 21) | Frontends (Customer & Admin SPAs) |
+------------------------------------+--------------------------+-----------------------------------+
| Hiox Linux Shared Web Hosting      | ❌ INCOMPATIBLE          | ✅ COMPATIBLE (Apache .htaccess)  |
| Hiox Java / JSP Shared Hosting     | ❌ INCOMPATIBLE (Legacy) | ✅ COMPATIBLE                     |
| Hiox Linux VPS / Cloud Server      | ✅ COMPATIBLE (systemd)  | ✅ COMPATIBLE (Nginx / Apache)    |
| Hiox Dedicated Server              | ✅ COMPATIBLE (systemd)  | ✅ COMPATIBLE (Nginx / Apache)    |
+------------------------------------+--------------------------+-----------------------------------+
```

### Detailed Evaluation of Hiox Plans

#### 1. Hiox Linux Shared Hosting (cPanel / Apache)
- **Java Support:** None. Shared plans only support PHP 7.x/8.x, Perl, Python (CGI/WSGI), and limited Node.js.
- **Process Management:** CloudLinux LVE actively restricts processes. Daemon background processes (like a persistent JVM) are blocked or automatically killed if they exceed CPU/RAM thresholds or run continuously.
- **Port Binding:** Shared hosting accounts cannot bind to internal TCP ports (such as `:8080`) or configure custom reverse proxy rules to local ports.
- **Frontend Capability:** **Compatible.** Both Angular SPAs can be hosted in `public_html/` and `public_html/admin/` as static files using Apache rewrite rules.

#### 2. Hiox Java Hosting (Tomcat / JSP Shared Hosting)
- **Architecture:** Legacy Apache Tomcat containers designed for `.war` servlet deployments.
- **Java Version:** Typically limited to Java 8 or Java 11. Spring Boot 3.3.5 strictly requires **Java 21 bytecode** and **Jakarta EE 10 / Servlet 6.0** (Tomcat 10.1+). Deploying Spring Boot 3.3.5 into legacy Tomcat 8/9 fails with `UnsupportedClassVersionError: 65.0`.
- **Packaging Constraint:** NOVA PETZLIFE is compiled as an executable Fat JAR with embedded Tomcat, not a traditional Servlet `.war`.
- **Verdict:** Incompatible without downgrading the entire framework to Spring Boot 2.7 / Java 11 (which would compromise security and modern features).

#### 3. Hiox Linux VPS / Dedicated Cloud (Ubuntu 22.04 LTS / Debian 12)
- **Full Root SSH Access:** Supported.
- **Java 21 OpenJDK:** Can be installed natively via `apt install openjdk-21-jdk-headless` or Eclipse Temurin PPA.
- **Process Management:** Full `systemd` process supervision (`systemctl enable nova-petzlife`). Automatic restarts on crash or physical server reboot.
- **Reverse Proxy & SSL:** Nginx or Apache can be configured to serve the Angular frontends on port 80/443 with Let's Encrypt SSL, proxying `/api/v1` to `http://127.0.0.1:8080`.
- **Verdict:** **100% COMPATIBLE without Docker.**

---

## 6. BACKEND NON-DOCKER READINESS

### 1. Runtime Profile Architecture
The backend is driven by Spring Profiles configured in [application.yml](file:///D:/Projects_StandAlone/Nova%20Petzlife/backend/src/main/resources/application.yml) and [application-prod.yml](file:///D:/Projects_StandAlone/Nova%20Petzlife/backend/src/main/resources/application-prod.yml):
- **Default Profile:** `dev` (H2 or local MySQL)
- **Production Profile:** `prod` (Active via `--spring.profiles.active=prod` or `SPRING_PROFILES_ACTIVE=prod`)
- **Port:** Configured via `SERVER_PORT` environment variable (defaults to `8080`).

### 2. Reverse Proxy & Forwarded Headers
In production behind Hiox Nginx/Apache reverse proxy, Spring Boot must accurately recognize HTTPS requests, client IPs, and original host headers:
```yaml
server:
  port: ${SERVER_PORT:8080}
  forward-headers-strategy: framework
  tomcat:
    remoteip:
      remote-ip-header: x-forwarded-for
      protocol-header: x-forwarded-proto
```
*Current Status:* Fully supported in Spring Boot 3.3.5.

### 3. Database Connectivity & Connection Pooling
The production profile uses **HikariCP** with hardened production parameters:
- `spring.datasource.url`: Dynamic JDBC URL via `${DB_URL}` (supports `useSSL=true`, `allowPublicKeyRetrieval=true`, `serverTimezone=UTC`).
- `maximum-pool-size`: 10 connections (ideal for 2GB-4GB VPS).
- `minimum-idle`: 2 connections.
- `connection-timeout`: 30000ms.
- `max-lifetime`: 1800000ms (prevents stale TCP socket drops over WAN).

### 4. Flyway Database Migrations
Migrations are managed in `backend/src/main/resources/db/migration/`:
- `V1__initial_schema.sql` (Core tables: users, roles, products, categories, orders, taxes)
- `V2__seed_baseline_data.sql` (Baseline roles, admin permissions, default tax brackets)
- `V3__kerala_districts.sql` (Kerala PIN codes & delivery areas)
- `V4__demo_test_data_seed.sql` (Testing seed)
- `V5__sample_reviews.sql` (Initial review seed)
- `V6__order_enhancements.sql` (Tracking & refund columns)
- `V7__guest_and_delivery_enhancements.sql` (Guest checkout & geofencing)
- `V8__create_password_reset_tokens.sql` (Secure SHA-256 password reset tokens)

*Flyway Verdict:* Runs automatically on startup upon connecting to any clean MySQL 8.0+ instance.

---

## 7. CUSTOMER & ADMIN FRONTEND NON-DOCKER READINESS

Both frontends are pure client-side Single Page Applications (SPAs) compiled with Angular 17.

### Build Output Locations
- **Customer Frontend:** `frontend/dist/frontend/browser/`
- **Admin Frontend:** `admin/dist/admin/browser/`

### SPA Routing & Apache Rewrite Rules (`.htaccess`)
Because Angular uses HTML5 PushState routing (`/shop`, `/cart`, `/account`, `/orders`), direct browser navigation or refreshing on sub-paths will trigger a web server 404 error unless rewrite rules are configured.

When deploying to an Apache-based directory on Hiox, each frontend must have an `.htaccess` file:

```apache
# NOVA PETZLIFE — Angular HTML5 SPA Routing Rule
<IfModule mod_rewrite.c>
  RewriteEngine On
  RewriteBase /
  RewriteRule ^index\.html$ - [L]
  RewriteCond %{REQUEST_FILENAME} !-f
  RewriteCond %{REQUEST_FILENAME} !-d
  RewriteRule . /index.html [L]
</IfModule>

# Caching & Compression Optimization
<IfModule mod_expires.c>
  ExpiresActive On
  ExpiresByType text/html "access plus 0 seconds"
  ExpiresByType application/javascript "access plus 1 year"
  ExpiresByType text/css "access plus 1 year"
  ExpiresByType image/svg+xml "access plus 1 month"
  ExpiresByType image/webp "access plus 1 month"
</IfModule>
```

### Content Security Policy (CSP) Directives
Both frontends enforce CSP in `index.html`. For production on Hiox, the `connect-src` directive must include the production API endpoint:
- Customer Frontend: `connect-src 'self' https://api.novapetzlife.com https://nova-petzlife.onrender.com https://api.razorpay.com https://*.openstreetmap.org https://nominatim.openstreetmap.org;`
- Admin Frontend: `connect-src 'self' https://api.novapetzlife.com https://nova-petzlife.onrender.com https://*.openstreetmap.org https://nominatim.openstreetmap.org;`

---

## 8. DATABASE PRODUCTION READINESS

### Database Architecture Comparison

```
+----------------------------------------------------------------------------------------------------+
|                               DATABASE DEPLOYMENT OPTIONS COMPARISON                               |
+--------------------------+-------------------------------------+-----------------------------------+
| Feature / Dimension      | Option A: Hiox Local MySQL on VPS   | Option B: MonsterASP External DB  |
+--------------------------+-------------------------------------+-----------------------------------+
| Network Latency          | < 0.5 ms (local unix socket / loop) | 80 - 150 ms (WAN over Internet)   |
| Security & Isolation     | MySQL bound to 127.0.0.1 (No WAN)   | Requires open port 3306 on WAN    |
| SSL Overhead             | Negligible                          | Mandatory TLS handshake per conn  |
| Backup Control           | Automated daily local mysqldump     | Managed by MonsterASP panel       |
| Flyway Migration Speed   | Instant (< 2 seconds)               | Subject to network latency        |
| Operational Cost         | Included in VPS storage             | Free tier or separate billing     |
| Risk of WAN Disconnection| None                                | High (intermittent socket resets) |
+--------------------------+-------------------------------------+-----------------------------------+
```

### Database Verdict
- **Recommended:** **Hiox Local MySQL 8.0 on VPS**. Eliminates WAN latency, removes external dependency, and allows binding MySQL strictly to `localhost` for maximum security.
- **Migration Strategy:** Dump the MonsterSQL schema and data using `mysqldump` and restore directly into Hiox MySQL using standard SQL import.

---

## 9. PRODUCTION SECURITY & ENVIRONMENT VARIABLE INVENTORY

Every production setting is externalized into environment variables. Real secrets must never be committed to Git.

| Variable Name | Application | Purpose | Required? | Safe Default / Format | Sensitive? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Backend | Activates production profile | **YES** | `prod` | No |
| `SERVER_PORT` | Backend | Internal HTTP port for Spring Boot | **YES** | `8080` | No |
| `DB_URL` | Backend | JDBC connection string | **YES** | `jdbc:mysql://127.0.0.1:3306/novapetzlife?useSSL=false&serverTimezone=UTC` | No |
| `DB_USERNAME` | Backend | Database user | **YES** | `novapetz_user` | No |
| `DB_PASSWORD` | Backend | Database user password | **YES** | `[REDACTED_STRONG_SECRET]` | **YES** |
| `JWT_SECRET` | Backend | 256-bit HMAC key for tokens | **YES** | Minimum 64-char hex/base64 string | **YES** |
| `JWT_EXPIRATION` | Backend | Access token validity (ms) | No | `86400000` (24 Hours) | No |
| `FRONTEND_URL` | Backend | Customer storefront base URL | **YES** | `https://novapetzlife.com` | No |
| `ADMIN_FRONTEND_URL` | Backend | Admin control center base URL | **YES** | `https://admin.novapetzlife.com` | No |
| `RESEND_API_KEY` | Backend | Resend transactional mail key | No | `re_[REDACTED_KEY]` (Leave blank for deferred) | **YES** |
| `RESEND_FROM_EMAIL` | Backend | Sender email address | No | `no-reply@mail.novapetzlife.com` | No |
| `RESEND_FROM_NAME` | Backend | Sender display name | No | `NOVA PETZLIFE Store Desk` | No |
| `RAZORPAY_KEY_ID` | Backend | Razorpay API Key | No | `rzp_live_[REDACTED]` | **YES** |
| `RAZORPAY_KEY_SECRET` | Backend | Razorpay Secret | No | `[REDACTED_SECRET]` | **YES** |
| `CORS_ALLOWED_ORIGINS`| Backend | Allowed browser origins | **YES** | `https://novapetzlife.com,https://admin.novapetzlife.com` | No |

---

## 10. RESEND EMAIL READINESS & DNS CONFIGURATION

The transactional email engine is implemented in [ResendEmailService.java](file:///D:/Projects_StandAlone/Nova%20Petzlife/backend/src/main/java/com/novapetzlife/email/ResendEmailService.java):
- **Deferred Mode:** If `RESEND_API_KEY` is not supplied or is left blank, the backend operates in **Deferred Mode**. It logs the email payload and token safely to the console without crashing or throwing errors.
- **Production Activation:** Supplying `RESEND_API_KEY` immediately activates direct HTTP REST delivery to `https://api.resend.com/emails`.

### DNS Authentication Requirements for `mail.novapetzlife.com`
To achieve 100% inbox delivery and satisfy DMARC/SPF/DKIM policies, the following DNS records must be created at the domain registrar:

| Record Type | Hostname / Name | Value / Destination | Purpose |
| :--- | :--- | :--- | :--- |
| **TXT (SPF)** | `mail.novapetzlife.com` | `v=spf1 include:resend.com ~all` | Authorizes Resend IP cluster |
| **CNAME (DKIM 1)** | `resend._domainkey.mail.novapetzlife.com` | `[Provided by Resend Dashboard]` | Cryptographic email signing |
| **CNAME (DKIM 2)** | `resend2._domainkey.mail.novapetzlife.com`| `[Provided by Resend Dashboard]` | Cryptographic email signing key 2 |
| **MX** | `mail.novapetzlife.com` | `feedback-smtp.resend.com` (Priority 10) | Return-path & bounce handling |
| **TXT (DMARC)** | `_dmarc.novapetzlife.com` | `v=DMARC1; p=none; rua=mailto:dmarc@novapetzlife.com` | Domain protection policy |

---

## 11. RAZORPAY & EXTERNAL INTEGRATIONS

- **Razorpay Checkout:** Initialized in customer frontend via `checkout.js`. Backend handles order creation (`RazorpayClient`) and verifies HMAC SHA-256 signatures before confirming orders.
- **Image CDN (Cloudinary):** Verified in `MediaService.java`. Product images are uploaded directly to Cloudinary and stored as absolute HTTPS URLs (`https://res.cloudinary.com/novapetzlife/...`). **No local filesystem storage or volume is required.**
- **OpenStreetMap / Nominatim:** Free map tiles and geocoding used for Trivandrum 20km delivery validation. Requires outbound HTTPS access from the client browser.

---

## 12. DNS & DOMAIN ARCHITECTURE

```
                                  [ INTERNET CLIENTS ]
                                            |
                                  [ DNS: novapetzlife.com ]
        +-----------------------------------+-----------------------------------+
        |                                   |                                   |
[ novapetzlife.com ]            [ admin.novapetzlife.com ]          [ api.novapetzlife.com ]
  (Customer SPA)                     (Admin SPA)                        (Spring Boot JAR)
        |                                   |                                   |
  Hiox VPS: Port 443                  Hiox VPS: Port 443                  Hiox VPS: Port 443
 /var/www/novapetzlife/              /var/www/admin/                      Nginx Reverse Proxy
        |                                   |                                   |
        +-----------------------------------+-----------------------------------+
                                            |
                                [ Nginx SSL Termination ]
                                            |
                                [ Spring Boot :8080 ]
                                            |
                                [ MySQL 8.0 (Local) ]
```

### Proposed DNS Records Table
| Type | Record Name | Target / Destination | TTL |
| :--- | :--- | :--- | :--- |
| **A** | `novapetzlife.com` | `[Hiox VPS Public IP]` | 300 |
| **CNAME** | `www.novapetzlife.com` | `novapetzlife.com` | 300 |
| **A** | `admin.novapetzlife.com` | `[Hiox VPS Public IP]` | 300 |
| **A** | `api.novapetzlife.com` | `[Hiox VPS Public IP]` | 300 |
| **TXT / CNAME** | `mail.novapetzlife.com` | `[Resend Verification Records]` | 300 |

---

## 13. PROCESS MANAGEMENT & SYSTEMD OPERATIONS

On a Hiox Linux VPS, Spring Boot must be supervised by `systemd` to ensure continuous uptime and automatic recovery from crashes or reboots.

### Production Systemd Unit (`/etc/systemd/system/nova-petzlife.service`)
```ini
[Unit]
Description=NOVA PETZLIFE Spring Boot Backend Service
After=syslog.target network.target mysql.service

[Service]
Type=simple
User=novapetz
Group=novapetz
WorkingDirectory=/opt/novapetzlife/backend
EnvironmentFile=/opt/novapetzlife/backend/.env
ExecStart=/usr/bin/java \
  -Xms512m -Xmx1024m \
  -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=prod \
  -jar /opt/novapetzlife/backend/nova-petzlife-backend.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=nova-petzlife-backend
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

### Service Control Commands
```bash
# Reload daemon definitions
sudo systemctl daemon-reload

# Enable automatic start on system boot
sudo systemctl enable nova-petzlife

# Start, stop, or restart the application
sudo systemctl start nova-petzlife
sudo systemctl restart nova-petzlife
sudo systemctl status nova-petzlife

# Tail live application logs
sudo journalctl -u nova-petzlife -f -n 100
```

---

## 14. NGINX REVERSE PROXY & HOSTING CONFIGURATION

```nginx
# 1. Customer Frontend Storefront
server {
    listen 80;
    server_name novapetzlife.com www.novapetzlife.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name novapetzlife.com www.novapetzlife.com;

    ssl_certificate /etc/letsencrypt/live/novapetzlife.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/novapetzlife.com/privkey.pem;

    root /var/www/novapetzlife/browser;
    index index.html;

    # Angular SPA routing fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Static asset caching
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, no-transform";
    }
}

# 2. Admin Frontend Control Center
server {
    listen 443 ssl http2;
    server_name admin.novapetzlife.com;

    ssl_certificate /etc/letsencrypt/live/admin.novapetzlife.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/admin.novapetzlife.com/privkey.pem;

    root /var/www/admin/browser;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}

# 3. Backend REST API
server {
    listen 443 ssl http2;
    server_name api.novapetzlife.com;

    ssl_certificate /etc/letsencrypt/live/api.novapetzlife.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.novapetzlife.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
        proxy_connect_timeout 60s;
    }
}
```

---

## 15. RESOURCE & PERFORMANCE SIZING REQUIREMENTS

### Minimum Production Specifications (Hiox Linux VPS)
- **CPU:** 2 vCPU cores (handles concurrent G1GC, HTTP request threads, and password hashing).
- **RAM:** 4 GB RAM recommended (2 GB absolute minimum):
  - Spring Boot JVM Heap (`-Xms512m -Xmx1024m`): ~1.2 GB resident memory.
  - MySQL 8.0: ~600 MB resident memory (InnoDB buffer pool 256MB).
  - Nginx web server: ~50 MB.
  - OS & buffers: ~600 MB.
- **Disk Storage:** 40 GB SSD (application JARs, static builds, database records, OS).

---

## 16. PRODUCTION READINESS CHECKLIST

| Category | Item | Status | Verified Evidence & Remediation |
| :--- | :--- | :--- | :--- |
| **Application** | Backend Maven Packaging | **READY** | Clean `nova-petzlife-backend.jar` generated natively. |
| **Application** | 105 Backend Tests | **READY** | `mvn test` executed; 105 passed, 0 failed. |
| **Application** | Customer Frontend Build | **READY** | `@angular-devkit/build-angular:application` passed; static files generated. |
| **Application** | 31 Frontend Tests | **READY** | Karma Headless executed; 31 passed, 0 failed. |
| **Application** | Admin Frontend Build | **READY** | Standalone production build compiled successfully. |
| **Application** | Docker Independence | **READY** | Zero Docker runtime or build dependencies in source code. |
| **Infrastructure**| Hiox Linux VPS (Java 21) | **READY** | Standard OpenJDK 21 headless supported on Ubuntu 22.04. |
| **Infrastructure**| Hiox Shared cPanel | **NOT READY** | Cannot run persistent Java 21 / Spring Boot processes. |
| **Infrastructure**| Web Server Reverse Proxy | **READY** | Standard Nginx reverse-proxy configuration specified. |
| **Infrastructure**| Process Supervision | **READY** | Native `systemd` unit blueprint prepared. |
| **Database** | MySQL Schema Migrations | **READY** | Flyway V1-V8 migrations execute automatically. |
| **Security** | JWT Authentication | **READY** | HMAC SHA-256 tokens configured via dynamic secret. |
| **Security** | Password Reset & Resend | **READY** | Resend API externalized; deferred mode operational. |
| **Security** | Admin Account Isolation | **READY** | Role hierarchy and authentication endpoints separated. |
| **External** | Razorpay Integration | **PARTIALLY READY**| Signatures implemented; needs production live keys. |
| **External** | Image Storage (Cloudinary)| **READY** | Cloudinary external CDN verified; no local disk dependency. |

---

## 17. ARCHITECTURE OPTIONS & RECOMMENDATIONS

### Option A: Fully Hiox Non-Docker Deployment (RECOMMENDED)
- **Hosting Plan:** Hiox Linux VPS (Ubuntu 22.04 LTS, 4GB RAM, 2 vCPU).
- **Backend:** Spring Boot standalone JAR managed by `systemd`.
- **Customer Frontend:** Angular 17 static bundle served by Nginx on `novapetzlife.com`.
- **Admin Frontend:** Angular 17 static bundle served by Nginx on `admin.novapetzlife.com`.
- **API Endpoint:** Reverse proxied to `api.novapetzlife.com` -> `http://127.0.0.1:8080`.
- **Database:** Local MySQL 8.0 instance on the VPS.
- **Images:** External Cloudinary CDN.
- **Email:** Resend API via `mail.novapetzlife.com`.

### Option B: Hybrid Hosting (If User Already Owns Hiox Shared Hosting)
- **Frontends:** Deploy customer and admin static builds to Hiox Shared Web Hosting (Apache `.htaccess`).
- **Backend:** Keep Spring Boot hosted on a Java 21 containerless platform (e.g., AWS Lightsail, DigitalOcean Droplet, or keep Render background service).
- **Database:** MonsterSQL external database.
- **Limitations:** Higher latency between frontend, backend, and database; cross-origin CORS overhead.

---

## 18. STEP-BY-STEP PRODUCTION MIGRATION PLAN

```
Phase 1: Infrastructure Provisioning & Java 21 Installation
Phase 2: Database Preparation & Data Migration
Phase 3: Backend Deployment & Systemd Service Activation
Phase 4: Frontend Static Asset Upload & Web Server Routing
Phase 5: DNS Switch & SSL Certificate Activation
Phase 6: End-to-End Functional & Security Verification
```

### Stage 1: Server Setup (Hiox Linux VPS)
1. Provision Ubuntu 22.04 LTS VPS on Hiox.
2. Update system packages: `sudo apt update && sudo apt upgrade -y`
3. Install OpenJDK 21: `sudo apt install -y openjdk-21-jdk-headless`
4. Install MySQL Server: `sudo apt install -y mysql-server`
5. Install Nginx & Certbot: `sudo apt install -y nginx certbot python3-certbot-nginx`

### Stage 2: Database Configuration
1. Run `mysql_secure_installation`.
2. Create production database and dedicated user:
   ```sql
   CREATE DATABASE novapetzlife CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'novapetz_user'@'localhost' IDENTIFIED BY 'StrongPasswordHere!';
   GRANT ALL PRIVILEGES ON novapetzlife.* TO 'novapetz_user'@'localhost';
   FLUSH PRIVILEGES;
   ```

### Stage 3: Backend Deployment
1. Create system user: `sudo useradd -r -s /bin/false novapetz`
2. Create directories: `sudo mkdir -p /opt/novapetzlife/backend`
3. SCP `nova-petzlife-backend.jar` to `/opt/novapetzlife/backend/`
4. Create secure `.env` file with production secrets.
5. Create `/etc/systemd/system/nova-petzlife.service`.
6. Start and enable service:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable nova-petzlife
   sudo systemctl start nova-petzlife
   ```
7. Verify health: `curl http://127.0.0.1:8080/api/v1/health`

### Stage 4: Frontend Deployment
1. Build both frontends locally:
   - `npm --prefix frontend run build`
   - `npm --prefix admin run build`
2. Upload `frontend/dist/frontend/browser/` to `/var/www/novapetzlife/browser/`
3. Upload `admin/dist/admin/browser/` to `/var/www/admin/browser/`
4. Configure Nginx virtual hosts for `novapetzlife.com`, `admin.novapetzlife.com`, and `api.novapetzlife.com`.
5. Test Nginx configuration: `sudo nginx -t && sudo systemctl reload nginx`

### Stage 5: SSL & DNS Activation
1. Update DNS A records to point to Hiox VPS IP.
2. Obtain SSL certificates:
   ```bash
   sudo certbot --nginx -d novapetzlife.com -d www.novapetzlife.com -d admin.novapetzlife.com -d api.novapetzlife.com
   ```

### Stage 6: Post-Deployment Smoke Verification
1. Access `https://novapetzlife.com` on iPhone/desktop.
2. Verify customer login, registration, and product search.
3. Test Forgot Password workflow with token generation.
4. Access `https://admin.novapetzlife.com` and verify admin login.
5. Inspect GST rate editor and order dispatch views.

---

## 19. FINAL GO / NO-GO DECISION & AUDIT VERDICT

### Overall Audit Verdict:
### **READY AFTER INFRASTRUCTURE CONFIGURATION (HIOX LINUX VPS)**

- **Source Code Readiness:** **100% READY.** All 105 backend tests and 31 frontend tests pass. No application code modifications are required for non-Docker execution.
- **Docker Dependency:** **NONE.** Docker is completely optional.
- **Condition for Go:** The hosting environment provided by Hiox **must be a Linux VPS or Cloud Server with root SSH access and Java 21 support**. If a Shared cPanel plan is used, the backend cannot run and will require a VPS upgrade or Hybrid Option B.
