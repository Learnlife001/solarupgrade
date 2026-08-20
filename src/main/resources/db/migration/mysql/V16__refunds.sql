-- Sending money back. See the h2 copy for why the capture id is separate from
-- the provider order id.
ALTER TABLE orders ADD COLUMN capture_reference VARCHAR(255);
ALTER TABLE orders ADD COLUMN refund_reference VARCHAR(255);
ALTER TABLE orders ADD COLUMN refunded_at DATETIME(6);
