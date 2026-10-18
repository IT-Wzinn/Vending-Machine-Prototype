-- ====================================================================
-- scripts/setup_dev_db.sql
-- Development Database & User Setup for Vending Machine Prototype
--
-- Instructions:
-- Run this script locally as the postgres administrator (e.g., via psql or pgAdmin).
-- Example in PowerShell:
--   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -h localhost -f scripts\setup_dev_db.sql
-- ====================================================================

-- 1. Create database if it does not already exist
SELECT 'CREATE DATABASE vending_machine_dev'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'vending_machine_dev')\gexec

-- 2. Create application user if it does not already exist
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE  rolname = 'vending_user') THEN

      -- NOTE: Replace 'vending_dev_password' with your desired local password
      CREATE ROLE vending_user WITH LOGIN PASSWORD 'vending_dev_password';
   END IF;
END
$do$;

-- 3. Grant permissions on database
GRANT ALL PRIVILEGES ON DATABASE vending_machine_dev TO vending_user;

-- 4. Grant schema-level permissions inside vending_machine_dev
\connect vending_machine_dev

GRANT ALL ON SCHEMA public TO vending_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO vending_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO vending_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO vending_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO vending_user;
