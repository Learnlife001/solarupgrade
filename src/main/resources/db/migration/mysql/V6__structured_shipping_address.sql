-- Split the shipping address into fields.
--
-- It was one free-text block, which cannot be validated, cannot be handed to a
-- courier's API, and cannot be searched or sorted afterwards.
--
-- Only line 1 is required. Line 2 and the postcode are genuinely optional --
-- most Nigerian addresses carry no postcode in daily use. City and state are
-- nullable at the database level purely so that orders placed before this
-- change still load; the checkout form requires them for anything new.

ALTER TABLE orders ADD COLUMN shipping_line1 VARCHAR(255);
ALTER TABLE orders ADD COLUMN shipping_line2 VARCHAR(255);
ALTER TABLE orders ADD COLUMN shipping_city VARCHAR(255);
ALTER TABLE orders ADD COLUMN shipping_state VARCHAR(255);
ALTER TABLE orders ADD COLUMN shipping_country VARCHAR(2);

-- Carry the old blob over verbatim into line 1. Splitting it on newlines would
-- be guessing which line was the city, and a wrong guess is worse than an
-- unstructured address that is at least still true.
UPDATE orders SET shipping_line1 = shipping_address WHERE shipping_line1 IS NULL;

ALTER TABLE orders DROP COLUMN shipping_address;

-- The postcode was NOT NULL from the original schema. It is genuinely optional
-- now, and leaving the constraint would only mean storing '' to mean "none" --
-- a blank string pretending to be a value.
ALTER TABLE orders MODIFY shipping_postcode VARCHAR(255) NULL;
