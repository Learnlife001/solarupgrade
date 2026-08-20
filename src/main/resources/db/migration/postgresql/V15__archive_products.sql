-- Retiring a product without losing what was sold.
--
-- Deleting one is not an option: order_items point at it, and an order from
-- last month has to keep saying what it was for. Order history that quietly
-- rewrites itself when the catalogue changes is worse than a stale catalogue.
--
-- So a product is archived instead: gone from the shop, still in the admin
-- list, still attached to every order that bought it, and restorable.
ALTER TABLE products ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

-- The shop reads this on every catalogue page, so it is worth an index.
CREATE INDEX idx_products_archived ON products (archived);
