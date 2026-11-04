-- "users", not "user": user is a reserved word in Postgres.
CREATE TABLE users (
    id        UUID         PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    email     VARCHAR(320) NOT NULL UNIQUE,   -- stored lowercased by the app
    company   VARCHAR(255),
    address   VARCHAR(500),
    timezone  VARCHAR(64)  NOT NULL DEFAULT 'UTC',  -- IANA name, e.g. Europe/London
    enrolled  BOOLEAN      NOT NULL DEFAULT FALSE
);
