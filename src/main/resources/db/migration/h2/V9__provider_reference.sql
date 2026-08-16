-- The payment provider's own id for this order -- a PayPal order id today, an
-- OPay reference later.
--
-- Needed because a webhook arrives with the provider's identifiers and nothing
-- else: without a stored reference there is no way to tie a notification back
-- to an order except by trusting whatever the caller says, which is exactly
-- what a webhook must not do. It also gives support a value to search for in
-- the provider's own dashboard.

ALTER TABLE orders ADD COLUMN provider_reference VARCHAR(64);
