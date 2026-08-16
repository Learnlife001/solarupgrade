-- Switch the shop's base currency to the naira, and record what the customer
-- is actually charged.
--
-- The catalogue was priced in pounds. The store sells into Nigeria and settles
-- through a Nigerian provider, so naira is the honest base: it is what the
-- totals are computed in and what the books are kept in. PayPal cannot charge
-- naira, so a PayPal order is converted to euro at checkout -- and the result
-- is stored here rather than recalculated, because the rate can move between
-- placing an order and paying for it and the customer must pay what they were
-- quoted.
--
-- exchange_rate is naira per unit of payment_currency, and is NULL when no
-- conversion happened. Keeping it makes the arithmetic on any past order
-- checkable instead of having to be trusted.

ALTER TABLE orders ADD COLUMN payment_currency VARCHAR(3);
ALTER TABLE orders ADD COLUMN payment_amount NUMERIC(12, 2);
ALTER TABLE orders ADD COLUMN exchange_rate NUMERIC(18, 8);

-- Reprice the seeded catalogue. Each row is matched on its old pound price as
-- well as its name, so this is a no-op against a database whose prices have
-- already been set by hand rather than silently overwriting them.
--
-- These are placeholder figures converted at a round rate. Set real ones
-- before taking real money.
UPDATE products SET price =  380000.00 WHERE name = '450W Monocrystalline Panel'          AND price =  189.00;
UPDATE products SET price =  330000.00 WHERE name = '410W Slimline Panel'                 AND price =  164.50;
UPDATE products SET price = 2490000.00 WHERE name = '5kW Hybrid Inverter'                 AND price = 1245.00;
UPDATE products SET price = 1500000.00 WHERE name = '3.6kW String Inverter'               AND price =  749.00;
UPDATE products SET price = 4780000.00 WHERE name = '5.2kWh Battery Module'               AND price = 2390.00;
UPDATE products SET price = 8300000.00 WHERE name = '10.4kWh Battery Module'              AND price = 4150.00;
UPDATE products SET price =  630000.00 WHERE name = 'Pitched Roof Mounting Kit (8 panels)' AND price =  315.00;
UPDATE products SET price =  860000.00 WHERE name = 'Flat Roof Ballast Frame (4 panels)'  AND price =  428.00;
UPDATE products SET price = 1800000.00 WHERE name = '7kW Tethered EV Charger'             AND price =  899.00;
UPDATE products SET price =  360000.00 WHERE name = 'Consumption Monitoring Kit'          AND price =  179.00;
