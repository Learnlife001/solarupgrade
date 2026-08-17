-- Give every product its own picture.
--
-- Product.getImage() fell back to /images/{category}.svg when image_url was
-- null, and there were seven files for ten products. Both panels showed the
-- same illustration, as did both inverters, both batteries and both mounting
-- kits -- so on the grid a customer saw identical pictures at different prices,
-- which reads as a broken shop rather than a range.
--
-- Matched on name AND the absence of an existing image, so this cannot
-- overwrite artwork that has already been set by hand.

UPDATE products SET image_url = '/images/panel-450w.svg'
    WHERE name = '450W Monocrystalline Panel' AND image_url IS NULL;
UPDATE products SET image_url = '/images/panel-410w.svg'
    WHERE name = '410W Slimline Panel' AND image_url IS NULL;
UPDATE products SET image_url = '/images/inverter-5kw.svg'
    WHERE name = '5kW Hybrid Inverter' AND image_url IS NULL;
UPDATE products SET image_url = '/images/inverter-3-6kw.svg'
    WHERE name = '3.6kW String Inverter' AND image_url IS NULL;
UPDATE products SET image_url = '/images/battery-5-2kwh.svg'
    WHERE name = '5.2kWh Battery Module' AND image_url IS NULL;
UPDATE products SET image_url = '/images/battery-10-4kwh.svg'
    WHERE name = '10.4kWh Battery Module' AND image_url IS NULL;
UPDATE products SET image_url = '/images/mounting-pitched.svg'
    WHERE name = 'Pitched Roof Mounting Kit (8 panels)' AND image_url IS NULL;
UPDATE products SET image_url = '/images/mounting-flat.svg'
    WHERE name = 'Flat Roof Ballast Frame (4 panels)' AND image_url IS NULL;
