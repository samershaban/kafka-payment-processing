-- Adds 5 random users to the users table, but only if there are no users yet apart from the 5 fixed seed
-- users that Flyway inserts (V2__seed_users.sql, ids 00000000-0000-4000-8000-00000000000N). Those always
-- exist once the table does, so they don't count. Safe to run any number of times: it adds users once.
-- Run automatically by the "postgres-seed" service on every `docker compose up`, or by hand:
--   psql -h localhost -U orders -d orders -f postgres/random-users.sql
--
-- The users table is created by Flyway when the Order Service starts, so on a brand-new database
-- this script just prints a notice until the Order Service has run once.

DO $$
DECLARE
    added integer;
    existing integer;
BEGIN
    IF to_regclass('public.users') IS NULL THEN
        RAISE NOTICE 'users table not found: start the Order Service once (Flyway creates it), then run docker compose up again';
        RETURN;
    END IF;

    SELECT count(*) INTO existing FROM users WHERE id::text NOT LIKE '00000000-0000-4000-8000-%';
    IF existing > 0 THEN
        RAISE NOTICE 'Skipped: % user(s) besides the seed users already exist. Total users: %',
            existing, (SELECT count(*) FROM users);
        RETURN;
    END IF;

    WITH picks AS (
        -- One row per new user, with independent random choices (random() runs per row)
        SELECT 1 + floor(random() * 16)::int AS f,   -- first name
               1 + floor(random() * 16)::int AS l,   -- last name
               1 + floor(random() * 10)::int AS c,   -- company (some are NULL)
               1 + floor(random() * 8)::int  AS p,   -- place: city, country and matching timezone
               1 + floor(random() * 10)::int AS s,   -- street
               1 + floor(random() * 250)::int AS n,  -- house number
               random() < 0.5                AS enrolled
        FROM generate_series(1, 5)
    ),
    people AS (
        SELECT (ARRAY['Amara','Liam','Sofia','Noah','Yuki','Mateo','Zara','Ethan',
                      'Leila','Oscar','Ines','Kai','Freya','Omar','Clara','Ravi'])[f] AS first,
               (ARRAY['Mensah','Novak','Rossi','Kim','Silva','Haddad','Larsen','Dubois',
                      'Patel','Moreno','Fischer','Tanaka','Walsh','Costa','Nguyen','Brennan'])[l] AS last,
               (ARRAY['Lumen Labs','Northwind Co','BrightPath','Sakura Foods','Bluefin Logistics',
                      'Copperleaf','Orbit Analytics', NULL, NULL, NULL])[c] AS company,
               (ARRAY['London','New York','Toronto','Berlin','São Paulo','Tokyo','Sydney','Mumbai'])[p] AS city,
               (ARRAY['UK','USA','Canada','Germany','Brazil','Japan','Australia','India'])[p] AS country,
               (ARRAY['Europe/London','America/New_York','America/Toronto','Europe/Berlin',
                      'America/Sao_Paulo','Asia/Tokyo','Australia/Sydney','Asia/Kolkata'])[p] AS timezone,
               (ARRAY['High Street','Maple Avenue','Station Road','Park Lane','King Street',
                      'Harbour Road','Elm Street','Market Square','River Walk','Church Lane'])[s] AS street,
               n,
               enrolled
        FROM picks
    )
    INSERT INTO users (id, name, email, company, address, timezone, enrolled)
    SELECT gen_random_uuid(),
           first || ' ' || last,
           -- random suffix keeps emails unique; .example is a reserved domain, so never a real address
           lower(first || '.' || last || '.' || substr(md5(random()::text), 1, 6) || '@example.com'),
           company,
           n || ' ' || street || ', ' || city || ', ' || country,
           timezone,
           enrolled
    FROM people
    ON CONFLICT (email) DO NOTHING;

    GET DIAGNOSTICS added = ROW_COUNT;
    RAISE NOTICE 'Added % random user(s). Total users: %', added, (SELECT count(*) FROM users);
END $$;
