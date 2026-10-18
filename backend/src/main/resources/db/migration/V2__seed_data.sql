-- V2__seed_data.sql
-- Seed initial machine VM001 and its three product slots
-- According to PRODUCT_SPEC.md §12 and docs/API_CONTRACT.md §2.2

-- 1. Insert default machine VM001
INSERT INTO machines (id, machine_code, name, status, message, created_at, updated_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440000',
    'VM001',
    'Máy bán hàng tầng 1',
    'ACTIVE',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 2. Insert 3 product slots for VM001
INSERT INTO products (id, machine_id, name, description, image_url, price_vnd, slot_number, stock_quantity, reserved_quantity, is_active, created_at, updated_at)
VALUES
(
    '550e8400-e29b-41d4-a716-446655440001',
    '550e8400-e29b-41d4-a716-446655440000',
    'Nước suối Aqua 500ml',
    'Nước uống tinh khiết Aqua chai 500ml',
    'https://example.com/images/aqua-500ml.jpg',
    8000,
    1,
    5,
    0,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '550e8400-e29b-41d4-a716-446655440002',
    '550e8400-e29b-41d4-a716-446655440000',
    'Snack Lay''s vị tự nhiên',
    NULL,
    NULL,
    15000,
    2,
    3,
    0,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    '550e8400-e29b-41d4-a716-446655440003',
    '550e8400-e29b-41d4-a716-446655440000',
    'Kẹo cao su Doublemint',
    NULL,
    NULL,
    5000,
    3,
    0,
    0,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
