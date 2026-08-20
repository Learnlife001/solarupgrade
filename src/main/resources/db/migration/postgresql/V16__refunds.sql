-- Sending money back.
--
-- capture_reference is the piece that was missing. provider_reference holds
-- the id of the order created at the provider; a refund is made against the
-- *capture* -- the individual movement of money -- which has an id of its own.
-- Without storing it there is nothing to refund against, which is why refunds
-- could not be built before now.
--
-- Nullable on every existing row: orders paid before this migration have no
-- capture id recorded, and no amount of guessing would produce one. Those have
-- to be refunded in the provider's own dashboard, and the admin page says so
-- rather than offering a button that cannot work.
ALTER TABLE orders ADD COLUMN capture_reference VARCHAR(255);

-- What came back, and when. The reason lives in the audit trail beside who did
-- it: this is the order's own record that money was returned.
ALTER TABLE orders ADD COLUMN refund_reference VARCHAR(255);
ALTER TABLE orders ADD COLUMN refunded_at TIMESTAMP(6) WITH TIME ZONE;
