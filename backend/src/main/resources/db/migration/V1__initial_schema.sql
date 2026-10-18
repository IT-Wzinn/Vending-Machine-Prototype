-- V1__initial_schema.sql
-- Vending Machine Prototype - Initial Database Schema
-- According to PRODUCT_SPEC.md §12

-- 1. Machines table
CREATE TABLE machines (
    id UUID PRIMARY KEY,
    machine_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Products table
CREATE TABLE products (
    id UUID PRIMARY KEY,
    machine_id UUID NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    image_url VARCHAR(500),
    price_vnd INTEGER NOT NULL CHECK (price_vnd > 0),
    slot_number INTEGER NOT NULL CHECK (slot_number IN (1, 2, 3)),
    stock_quantity INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_machine_slot UNIQUE (machine_id, slot_number)
);

-- 3. Orders table
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    public_order_code VARCHAR(50) NOT NULL UNIQUE,
    machine_id UUID NOT NULL REFERENCES machines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity = 1),
    unit_price_vnd INTEGER NOT NULL CHECK (unit_price_vnd > 0),
    total_amount_vnd INTEGER NOT NULL CHECK (total_amount_vnd > 0),
    transfer_reference VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    dispensing_started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Order Items table
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price_vnd INTEGER NOT NULL CHECK (unit_price_vnd > 0),
    subtotal_vnd INTEGER NOT NULL CHECK (subtotal_vnd > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. Payment Transactions table
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    provider_transaction_id VARCHAR(100) NOT NULL,
    order_id UUID REFERENCES orders(id),
    amount_vnd INTEGER NOT NULL CHECK (amount_vnd > 0),
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    direction VARCHAR(20) NOT NULL DEFAULT 'INCOMING',
    transaction_status VARCHAR(50) NOT NULL,
    transfer_reference VARCHAR(100),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    raw_payload TEXT,
    verification_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_provider_transaction UNIQUE (provider, provider_transaction_id)
);

-- 6. Dispense Commands table
CREATE TABLE dispense_commands (
    id UUID PRIMARY KEY,
    command_id UUID NOT NULL UNIQUE,
    order_id UUID NOT NULL REFERENCES orders(id),
    machine_id UUID NOT NULL REFERENCES machines(id),
    slot_number INTEGER NOT NULL CHECK (slot_number IN (1, 2, 3)),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    published_at TIMESTAMP WITH TIME ZONE,
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 7. Device Events table
CREATE TABLE device_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    machine_id UUID NOT NULL REFERENCES machines(id),
    command_id UUID REFERENCES dispense_commands(command_id),
    event_type VARCHAR(50) NOT NULL,
    slot_number INTEGER,
    payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 8. Admin Users table
CREATE TABLE admin_users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    token_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_products_machine_id ON products(machine_id);
CREATE INDEX idx_products_is_active ON products(is_active);
CREATE INDEX idx_orders_machine_id ON orders(machine_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_transfer_ref ON orders(transfer_reference);
CREATE INDEX idx_orders_payment_expires ON orders(payment_expires_at);
CREATE INDEX idx_payment_tx_order_id ON payment_transactions(order_id);
CREATE INDEX idx_payment_tx_transfer_ref ON payment_transactions(transfer_reference);
CREATE INDEX idx_dispense_commands_order_id ON dispense_commands(order_id);
CREATE INDEX idx_dispense_commands_machine_id ON dispense_commands(machine_id);
CREATE INDEX idx_device_events_machine_id ON device_events(machine_id);
CREATE INDEX idx_device_events_event_type ON device_events(event_type);
