-- Sample users with FIXED ids, so existing orders (customerId c-1 .. c-5) can be re-linked to them
-- (scripts/backfill-customer-ids.sh) and so there's always a known user for curl examples.
-- Names and companies are fictional; emails use the reserved .example domain.
INSERT INTO users (id, name, email, company, address, timezone, enrolled) VALUES
  ('00000000-0000-4000-8000-000000000001', 'Maya Okafor',   'maya.okafor@lumen-labs.example',   'Lumen Labs',
   '18 Harbour Street, Bristol BS1 4QA, UK',          'Europe/London',       true),
  ('00000000-0000-4000-8000-000000000002', 'Daniel Ruiz',   'daniel.ruiz@northwind-co.example', 'Northwind Co',
   '420 Market Street, San Francisco, CA 94111, USA', 'America/Los_Angeles', true),
  ('00000000-0000-4000-8000-000000000003', 'Priya Natarajan', 'priya@brightpath.example',       'BrightPath',
   '7 Residency Road, Bengaluru 560025, India',       'Asia/Kolkata',        false),
  ('00000000-0000-4000-8000-000000000004', 'Tom Becker',    'tom.becker@kiezwerk.example',      NULL,
   'Oranienstraße 12, 10999 Berlin, Germany',         'Europe/Berlin',       false),
  ('00000000-0000-4000-8000-000000000005', 'Hana Sato',     'hana.sato@sakura-foods.example',   'Sakura Foods',
   '3-1 Marunouchi, Chiyoda, Tokyo 100-0005, Japan',  'Asia/Tokyo',          true);
