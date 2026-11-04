-- Prepares the local Postgres for change data capture (CDC) of the users table, so the analytics service
-- (Debezium) can stream every insert/update/delete in real time. Analytics plan, step 0.2.
--
-- Run as the superuser, connected to the "orders" database:
--   psql -h localhost -U postgres -d orders -f scripts/setup-postgres-cdc.sql
-- Then restart Postgres (wal_level only changes on restart). For the EnterpriseDB install on macOS:
--   sudo launchctl kickstart -k system/postgresql-15
-- Run the script again afterwards: it changes nothing the second time and ends with a status check.
--
-- On the first run, Postgres prints 'WARNING: "wal_level" is insufficient to publish logical changes': expected,
-- because the new wal_level only applies after the restart.
--
-- Safe to run any number of times. Note: the restart briefly disconnects every database on this server
-- (also other projects' databases), and logical WAL is slightly larger than the default "replica" level.

\set ON_ERROR_STOP on

-- 1. Write row-level changes to the WAL in a form logical decoding (pgoutput) can read. Takes effect on restart.
--    ALTER SYSTEM writes postgresql.auto.conf, so postgresql.conf itself isn't edited.
ALTER SYSTEM SET wal_level = 'logical';

-- 2. Let the app's role open a replication connection (Debezium streams changes over one).
ALTER ROLE orders WITH REPLICATION;

-- 3. A publication: which tables' changes are streamed. Only users for now.
DO $$
BEGIN
    IF to_regclass('public.users') IS NULL THEN
        RAISE EXCEPTION 'Table public.users not found. Run this in the "orders" database (-d orders), after the Order Service has started once (Flyway creates the table).';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'analytics_users') THEN
        CREATE PUBLICATION analytics_users FOR TABLE public.users;
        RAISE NOTICE 'Created publication analytics_users';
    ELSE
        RAISE NOTICE 'Publication analytics_users already exists';
    END IF;
    -- The publication must be owned by the role Debezium connects as, or Debezium can't manage it
    ALTER PUBLICATION analytics_users OWNER TO orders;
END $$;

-- 4. Status. wal_level shows "replica" until Postgres has been restarted.
SELECT
    current_setting('wal_level')                                              AS wal_level,
    CASE WHEN current_setting('wal_level') = 'logical'
         THEN 'ready' ELSE 'restart Postgres, then run this script again' END AS next_step,
    (SELECT rolreplication FROM pg_roles WHERE rolname = 'orders')            AS orders_can_replicate,
    (SELECT string_agg(schemaname || '.' || tablename, ', ')
       FROM pg_publication_tables WHERE pubname = 'analytics_users')          AS published_tables;
