#!/bin/bash
# 初始化本地开发数据库
# Run: bash scripts/setup_db.sh

echo "Creating database and user..."
mysql -u root <<'SQL'
CREATE DATABASE IF NOT EXISTS admission_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'admission'@'localhost' IDENTIFIED BY 'admission123';
GRANT ALL PRIVILEGES ON admission_platform.* TO 'admission'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "Verifying connection..."
mysql -u admission -padmission123 admission_platform -e "SELECT 'Connection OK' AS status;"

echo ""
echo "Database setup complete!"
echo "  Database: admission_platform"
echo "  User: admission"
echo "  Password: admission123"
echo ""
echo "Application will auto-run Flyway migrations on startup."
echo "Start the backend: cd backend && mvn spring-boot:run -pl admission-boot"
