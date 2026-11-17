# NOVA PETZLIFE — PRODUCTION DEPLOYMENT GUIDE (STANDALONE SPRING BOOT JAR & NGINX)

## 1. Executive Summary

This document describes the production deployment architecture for the **NOVA PETZLIFE E-Commerce Platform**, migrating from Docker-based deployment to an **executable Java 21 Spring Boot JAR** with external **MySQL 8.4+** database and **Nginx** reverse proxy for the Angular 17 customer storefront and admin portal.

---

## 2. Architecture Overview

```
+--------------------------------------------------------------------------------+
|                               INTERNET / USERS                                 |
+---------------------------------------+----------------------------------------+
                                        | HTTPS (443) / HTTP (80)
                                        v
+--------------------------------------------------------------------------------+
|                          NGINX REVERSE PROXY & WEB SERVER                      |
|                                                                                |
|  • Customer Storefront: /                      --> /var/www/nova-petzlife/store|
|  • Admin Portal:        /admin                 --> /var/www/nova-petzlife/admin|
|  • REST API Reverse:    /api/v1/*              --> http://127.0.0.1:8080/      |
|  • Health Actuator:     /actuator/*            --> http://127.0.0.1:8080/      |
+---------------------------------------+----------------------------------------+
                                        | Local TCP Loopback
                                        v
+--------------------------------------------------------------------------------+
|                 SPRING BOOT 3.3.5 EXECUTABLE JAR (JAVA 21)                     |
|                                                                                |
|  • Service: nova-petzlife.service (Systemd Managed)                            |
|  • Artifact: /opt/nova-petzlife/backend/nova-petzlife-backend.jar              |
|  • Port: 8080 (Bound to 127.0.0.1)                                             |
|  • Flyway Database Migrations: V1 -> V2 -> V3 -> V4 Automatic Execution        |
|  • Geolocation Engine: 20 km Haversine from Kuravankonam (8.528448, 76.953268) |
+---------------------------------------+----------------------------------------+
                                        | JDBC / SQL Port 3306
                                        v
+--------------------------------------------------------------------------------+
|                        MYSQL 8.4+ DATABASE SERVER                              |
|                                                                                |
|  • Database: nova_petzlife                                                     |
|  • Character Set: utf8mb4 / Collation: utf8mb4_unicode_ci                      |
|  • Dedicated User: nova_user                                                   |
+--------------------------------------------------------------------------------+
```

---

## 3. System Prerequisites

| Component | Minimum Version | Recommended Specification |
|---|---|---|
| Operating System | Ubuntu 22.04 LTS / Debian 12 / RHEL 9 | 64-bit Linux |
| CPU | 2 Cores | 4 Cores |
| RAM | 4 GB | 8 GB |
| Disk Storage | 30 GB SSD | 50 GB NVMe SSD |
| Java Runtime | OpenJDK 21 (LTS) | `temurin-21-jdk` or `openjdk-21-jdk` |
| Database Engine | MySQL 8.0.36+ or MySQL 8.4 LTS | MySQL 8.4 LTS |
| Web Server | Nginx 1.22+ | Nginx with TLS 1.3 |
| Node.js (Build only) | Node.js 20.x LTS | Node.js 20 + npm |
| Maven (Build only) | Apache Maven 3.9+ | Bundled with repo or apt |

### Installing Java 21 on Debian/Ubuntu
```bash
sudo apt update
sudo apt install -y openjdk-21-jdk-headless
java -version
```

---

## 4. Database Setup (MySQL 8.4+)

### 4.1 Create Database & Dedicated User
Log in as MySQL root administrator:
```bash
sudo mysql -u root -p
```

Execute the following database initialization statements:
```sql
CREATE DATABASE IF NOT EXISTS nova_petzlife 
  DEFAULT CHARACTER SET utf8mb4 
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'nova_user'@'localhost' IDENTIFIED BY 'PetzlifeProd@2026';

GRANT ALL PRIVILEGES ON nova_petzlife.* TO 'nova_user'@'localhost';
FLUSH PRIVILEGES;

-- Verify collation and connectivity
USE nova_petzlife;
SHOW VARIABLES LIKE 'character_set_database';
EXIT;
```

---

## 5. Building Deployment Artifacts

### 5.1 Build Spring Boot Executable JAR
In the project workspace:
```bash
cd backend
mvn clean package -DskipTests=false
```
The build creates the standalone executable JAR:
```
backend/target/nova-petzlife-backend.jar
```
Verify size: ~55 MB to ~65 MB.

### 5.2 Build Customer Storefront & Admin Portal
```bash
# Build Customer Storefront
cd ../frontend
npm install
npm run build -- --configuration production

# Build Admin Portal
cd ../admin
npm install
npm run build -- --configuration production
```
Artifacts are generated in:
- `frontend/dist/frontend/browser/`
- `admin/dist/admin/browser/`

---

## 6. Server Directory Layout

Create the standard production directory structure:
```bash
sudo mkdir -p /opt/nova-petzlife/backend
sudo mkdir -p /opt/nova-petzlife/config
sudo mkdir -p /var/log/nova-petzlife
sudo mkdir -p /var/www/nova-petzlife/store
sudo mkdir -p /var/www/nova-petzlife/admin

# Create dedicated unprivileged system user
sudo useradd -r -s /bin/false -d /opt/nova-petzlife nova
sudo chown -R nova:nova /opt/nova-petzlife
sudo chown -R nova:nova /var/log/nova-petzlife
```

Copy the built artifacts:
```bash
# Deploy Backend JAR
sudo cp backend/target/nova-petzlife-backend.jar /opt/nova-petzlife/backend/

# Deploy Customer Frontend
sudo cp -r frontend/dist/frontend/browser/* /var/www/nova-petzlife/store/

# Deploy Admin Frontend
sudo cp -r admin/dist/admin/browser/* /var/www/nova-petzlife/admin/

sudo chown -R www-data:www-data /var/www/nova-petzlife
```

---

## 7. Configuration & Environment Variables

Create environment configuration file `/opt/nova-petzlife/config/application-prod.env`:
```bash
sudo nano /opt/nova-petzlife/config/application-prod.env
```

```ini
# Spring Active Profiles
SPRING_PROFILES_ACTIVE=prod

# Server Port & Binding
SERVER_PORT=8080
SERVER_ADDRESS=127.0.0.1

# MySQL Database Credentials
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/nova_petzlife?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8
SPRING_DATASOURCE_USERNAME=nova_user
SPRING_DATASOURCE_PASSWORD=PetzlifeProd@2026
SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver

# JPA / Hibernate
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_JPA_SHOW_SQL=false
SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect

# Flyway Database Migration
SPRING_FLYWAY_ENABLED=true
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
SPRING_FLYWAY_BASELINE_VERSION=0

# Security & JWT Token Authentication
APP_JWT_SECRET=c2VjdXJlX25vdmFfcGV0emxpZmVfa2V5XzIwMjZfZHVtbXlfMzJieXRlcw==
APP_JWT_EXPIRATION_HOURS=24
APP_CORS_ALLOWED_ORIGINS=https://novapetzlife.com,https://admin.novapetzlife.com,http://localhost:4200,http://localhost:4201

# Delivery & Shop Coordinates (Kuravankonam Junction)
NOVA_PETZLIFE_SHOP_NAME=Petzlife (NOVA PETZLIFE)
NOVA_PETZLIFE_SHOP_LAT=8.528448
NOVA_PETZLIFE_SHOP_LNG=76.953268
NOVA_PETZLIFE_MAX_RADIUS_KM=20.0
```

Secure the environment file:
```bash
sudo chown nova:nova /opt/nova-petzlife/config/application-prod.env
sudo chmod 600 /opt/nova-petzlife/config/application-prod.env
```

---

## 8. Systemd Service Unit File

Create the systemd unit file `/etc/systemd/system/nova-petzlife.service`:
```bash
sudo nano /etc/systemd/system/nova-petzlife.service
```

```ini
[Unit]
Description=NOVA PETZLIFE E-Commerce Backend REST API
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=nova
Group=nova
WorkingDirectory=/opt/nova-petzlife/backend
EnvironmentFile=/opt/nova-petzlife/config/application-prod.env

ExecStart=/usr/bin/java \
    -server \
    -Xms1024m \
    -Xmx2048m \
    -XX:+UseG1GC \
    -XX:+ExplicitGCInvokesConcurrent \
    -Dfile.encoding=UTF-8 \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /opt/nova-petzlife/backend/nova-petzlife-backend.jar

SuccessExitStatus=143
Restart=always
RestartSec=10

StandardOutput=append:/var/log/nova-petzlife/backend.log
StandardError=append:/var/log/nova-petzlife/backend-error.log

LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

Reload systemd daemon, enable on boot, and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable nova-petzlife.service
sudo systemctl start nova-petzlife.service
sudo systemctl status nova-petzlife.service
```

---

## 9. Nginx Reverse Proxy Configuration

Create Nginx site configuration `/etc/nginx/sites-available/nova-petzlife.conf`:
```bash
sudo nano /etc/nginx/sites-available/nova-petzlife.conf
```

```nginx
# Customer Storefront & API Reverse Proxy
server {
    listen 80;
    server_name novapetzlife.com www.novapetzlife.com;

    root /var/www/nova-petzlife/store;
    index index.html;

    # Gzip Compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;

    # Angular HTML5 PushState Routing
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Backend REST API Proxy
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Spring Boot Actuator Health Probe
    location /actuator/ {
        proxy_pass http://127.0.0.1:8080/actuator/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }

    # Static Assets Cache
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 30d;
        add_header Cache-Control "public, no-transform";
    }
}

# Admin Portal Virtual Host
server {
    listen 80;
    server_name admin.novapetzlife.com;

    root /var/www/nova-petzlife/admin;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Enable the configuration and reload Nginx:
```bash
sudo ln -s /etc/nginx/sites-available/nova-petzlife.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## 10. Verification, Health Probes & Monitoring

### 10.1 Spring Boot Actuator Health Check
Verify backend service health:
```bash
curl -f http://127.0.0.1:8080/actuator/health
```
Expected output:
```json
{"status":"UP"}
```

### 10.2 Delivery Eligibility Smoke Test
Test the 20 km straight-line delivery endpoint from Kuravankonam:
```bash
# Kowdiar test (should be eligible ~1.2 km)
curl -X POST http://127.0.0.1:8080/api/v1/delivery/check-eligibility \
  -H "Content-Type: application/json" \
  -d '{"latitude": 8.524, "longitude": 76.965, "orderSubtotal": 650}'

# Neyyattinkara test (should be out-of-range ~24 km)
curl -X POST http://127.0.0.1:8080/api/v1/delivery/check-eligibility \
  -H "Content-Type: application/json" \
  -d '{"latitude": 8.401, "longitude": 77.086, "orderSubtotal": 650}'
```

---

## 11. Maintenance, Logging & Backup Procedures

### 11.1 Viewing Live Logs
```bash
# View backend service live output
sudo journalctl -u nova-petzlife.service -f

# View application logs
tail -f /var/log/nova-petzlife/backend.log
tail -f /var/log/nova-petzlife/backend-error.log
```

### 11.2 Automated Daily MySQL Database Backup
Add a cron job under `/etc/cron.daily/nova-petzlife-db-backup`:
```bash
sudo nano /etc/cron.daily/nova-petzlife-db-backup
```
```bash
#!/bin/bash
BACKUP_DIR="/var/backups/nova-petzlife"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
mkdir -p "$BACKUP_DIR"

mysqldump -u nova_user -p'PetzlifeProd@2026' --single-transaction --quick nova_petzlife | gzip > "$BACKUP_DIR/nova_petzlife_$TIMESTAMP.sql.gz"

# Rotate backups older than 30 days
find "$BACKUP_DIR" -type f -name "*.sql.gz" -mtime +30 -delete
```
```bash
sudo chmod +x /etc/cron.daily/nova-petzlife-db-backup
```

### 11.3 Applying Application Updates
When deploying a new version:
```bash
# 1. Stop service
sudo systemctl stop nova-petzlife.service

# 2. Replace JAR
sudo cp nova-petzlife-backend-new.jar /opt/nova-petzlife/backend/nova-petzlife-backend.jar
sudo chown nova:nova /opt/nova-petzlife/backend/nova-petzlife-backend.jar

# 3. Start service
sudo systemctl start nova-petzlife.service

# 4. Check status and health
sudo systemctl status nova-petzlife.service
curl -f http://127.0.0.1:8080/actuator/health
```
