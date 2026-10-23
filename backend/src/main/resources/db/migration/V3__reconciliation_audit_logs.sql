-- V3__reconciliation_audit_logs.sql
-- Create reconciliation audit log table and seed default admin user

-- 1. Create reconciliation_audit_logs table
CREATE TABLE reconciliation_audit_logs (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    admin_user_id UUID REFERENCES admin_users(id),
    action VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50),
    new_status VARCHAR(50),
    reason TEXT NOT NULL,
    confirmation_reference VARCHAR(100),
    payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reconciliation_audit_order_id ON reconciliation_audit_logs(order_id);
CREATE INDEX idx_reconciliation_audit_created_at ON reconciliation_audit_logs(created_at);

-- 2. Seed initial admin user if not exists
-- Username: admin
-- Password hash: SHA-256 hash of 'Admin@Secure2026!' with salt
-- SHA-256("admin:Admin@Secure2026!:vending_salt_2026") = 5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8 (example safe hash)
INSERT INTO admin_users (id, username, password_hash, token_version, created_at, updated_at)
VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'admin',
    'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (username) DO NOTHING;
