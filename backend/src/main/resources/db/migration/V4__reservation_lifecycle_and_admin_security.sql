-- V4__reservation_lifecycle_and_admin_security.sql

-- 1. Add is_reservation_held column to orders to accurately track reservation lifecycle
ALTER TABLE orders ADD COLUMN IF NOT EXISTS is_reservation_held BOOLEAN NOT NULL DEFAULT true;

-- 2. Backfill terminal orders where reservation was already released
UPDATE orders
SET is_reservation_held = false
WHERE status IN ('COMPLETED', 'CANCELLED', 'EXPIRED', 'DISPENSE_FAILED');

-- 3. Backfill NEEDS_RECONCILIATION orders that originated from late payment on expired orders.
-- When an order expired, its reservation was released back to inventory pool.
-- A subsequent late payment transitioned the order to NEEDS_RECONCILIATION without re-reserving stock.
-- Therefore, any NEEDS_RECONCILIATION order with a LATE_PAYMENT transaction does NOT hold reservation.
UPDATE orders o
SET is_reservation_held = false
WHERE o.status = 'NEEDS_RECONCILIATION'
  AND EXISTS (
      SELECT 1 FROM payment_transactions pt
      WHERE pt.order_id = o.id
        AND pt.verification_status = 'LATE_PAYMENT'
  );

-- 4. Safely remove unusable dummy admin seed from V3 if still matching the test hash
DELETE FROM admin_users
WHERE id = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
  AND username = 'admin'
  AND password_hash = 'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3';
