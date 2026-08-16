-- Record how the customer chose to pay.
--
-- The column holds the enum name only -- CARD, PAYPAL, APPLE_PAY, SEPA, KLARNA.
-- No card number, IBAN or token is stored here, and none is collected: those
-- belong to the provider's own hosted page, never to this schema.
--
-- Nullable, because orders placed before this column existed have no choice
-- recorded and back-filling one would be inventing data.

ALTER TABLE orders ADD COLUMN payment_method VARCHAR(32);
