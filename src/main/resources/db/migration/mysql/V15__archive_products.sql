-- Retiring a product without losing what was sold. See the h2 copy for why
-- this is a flag rather than a DELETE.
ALTER TABLE products ADD COLUMN archived BIT NOT NULL DEFAULT b'0';

CREATE INDEX idx_products_archived ON products (archived);
