-- Seed the product catalogue.
--
-- Reference data rather than demo data: a storefront with no products is
-- broken, so this belongs on every database including production. The demo
-- *account* is a different matter and stays behind the app.seed-demo-data
-- flag, because it ships a password published in the README.
--
-- Safe to run against a database that already has products: the WHERE NOT
-- EXISTS guard makes it a no-op rather than creating duplicates.

INSERT INTO products (name, description, price, category, stock)
SELECT * FROM (
    SELECT '450W Monocrystalline Panel' AS name,
           'High-efficiency mono PERC panel with a 25-year performance warranty. Suited to pitched domestic roofs.' AS description,
           189.00 AS price, 'PANEL' AS category, 120 AS stock
    UNION ALL SELECT '410W Slimline Panel',
           'Lower-profile panel for roofs where space is tight, with an all-black frame.',
           164.50, 'PANEL', 80
    UNION ALL SELECT '5kW Hybrid Inverter',
           'Single-phase hybrid inverter with battery support and built-in monitoring.',
           1245.00, 'INVERTER', 24
    UNION ALL SELECT '3.6kW String Inverter',
           'Entry-level string inverter for smaller arrays. 10-year warranty.',
           749.00, 'INVERTER', 31
    UNION ALL SELECT '5.2kWh Battery Module',
           'Stackable LiFePO4 storage module. Pairs with the 5kW hybrid inverter.',
           2390.00, 'BATTERY', 15
    UNION ALL SELECT '10.4kWh Battery Module',
           'Double-capacity storage for households running heat pumps or EV charging overnight.',
           4150.00, 'BATTERY', 7
    UNION ALL SELECT 'Pitched Roof Mounting Kit (8 panels)',
           'Anodised aluminium rails, clamps and roof hooks for a standard 8-panel array.',
           315.00, 'MOUNTING', 45
    UNION ALL SELECT 'Flat Roof Ballast Frame (4 panels)',
           'Non-penetrating ballasted frame set at 10 degrees for flat roofs.',
           428.00, 'MOUNTING', 18
    UNION ALL SELECT '7kW Tethered EV Charger',
           'Smart charger with scheduling, solar-surplus matching and app control.',
           899.00, 'EV_CHARGER', 22
    UNION ALL SELECT 'Consumption Monitoring Kit',
           'CT clamps and gateway giving real-time generation, export and household usage.',
           179.00, 'MONITORING', 60
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM products);
