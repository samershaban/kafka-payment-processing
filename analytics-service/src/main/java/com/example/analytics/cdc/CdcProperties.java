package com.example.analytics.cdc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for capturing changes to the users table (app.cdc.* in application.yml).
 *
 * @param enabled      start the Debezium engine with the app
 * @param host         Postgres host
 * @param port         Postgres port
 * @param database     database name
 * @param user         role to connect as; needs the REPLICATION attribute (scripts/setup-postgres-cdc.sql)
 * @param password     its password
 * @param publication  Postgres publication listing the captured tables (created by the setup script)
 * @param slot         replication slot name; Postgres keeps WAL for this slot until it's read
 * @param table        table to capture, schema-qualified
 * @param offsetFile   where Debezium remembers how far it has read, so a restart resumes instead of re-reading
 */
@ConfigurationProperties(prefix = "app.cdc")
public record CdcProperties(
        boolean enabled,
        String host,
        int port,
        String database,
        String user,
        String password,
        String publication,
        String slot,
        String table,
        String offsetFile
) {}
