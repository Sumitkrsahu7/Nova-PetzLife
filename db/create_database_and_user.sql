-- =============================================================================
-- NOVA PETZLIFE — MySQL Database & User Initialization Script
-- Execute this script as MySQL root/admin:
--   mysql -u root -p < db/create_database_and_user.sql
-- =============================================================================

-- 1. Create Application Database
CREATE DATABASE IF NOT EXISTS `nova_petzlife`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 2. Create Dedicated Application User 'novapetz'
-- IMPORTANT: Replace 'replace_with_your_application_database_password' with your strong application password:
CREATE USER IF NOT EXISTS 'novapetz'@'localhost' IDENTIFIED BY 'replace_with_your_application_database_password';

-- 3. Grant Application Privileges on 'nova_petzlife' Database
-- Grants DDL and DML operations needed for Flyway migrations and Spring Boot application runtime
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, REFERENCES, INDEX, ALTER, CREATE TEMPORARY TABLES, LOCK TABLES, EXECUTE
  ON `nova_petzlife`.* TO 'novapetz'@'localhost';

-- 4. Apply Privilege Changes
FLUSH PRIVILEGES;

-- 5. Safe Verification Queries
SELECT User, Host FROM mysql.user WHERE User = 'novapetz';
SHOW GRANTS FOR 'novapetz'@'localhost';
SHOW DATABASES LIKE 'nova_petzlife';
