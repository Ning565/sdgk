-- =============================================================
-- MySQL Initialization Script
-- Executed on first container start by docker-entrypoint-initdb.d
-- Creates the database and sets up basics before Flyway runs.
-- =============================================================

-- Ensure utf8mb4 everywhere
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- The database is already created via MYSQL_DATABASE env var.
-- This script handles additional initialization.

USE admission_platform;

-- -----------------------------------------------------------
-- Flyway schema history table (created by Flyway if not exists)
-- The CREATE below ensures it exists even if Flyway hasn't run yet,
-- but Flyway will manage it automatically.
-- We include it here so the database is "Flyway-ready" from the start.
-- -----------------------------------------------------------
-- Flyway creates this automatically; no need to pre-create it.

-- -----------------------------------------------------------
-- Set global defaults for the database
-- -----------------------------------------------------------
ALTER DATABASE admission_platform
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- -----------------------------------------------------------
-- Create a read-only application user (for application use, not root)
-- -----------------------------------------------------------
-- DROP USER IF EXISTS 'app_user'@'%';
-- CREATE USER 'app_user'@'%' IDENTIFIED BY 'app_pass_2024';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON admission_platform.* TO 'app_user'@'%';
-- FLUSH PRIVILEGES;

-- -----------------------------------------------------------
-- Verify / diagnostics
-- -----------------------------------------------------------
SELECT 'MySQL initialization complete. Database: admission_platform' AS status;
SHOW VARIABLES LIKE 'character_set_database';
SHOW VARIABLES LIKE 'collation_database';
