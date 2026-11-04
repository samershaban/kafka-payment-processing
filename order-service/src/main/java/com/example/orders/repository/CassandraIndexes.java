package com.example.orders.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the Cassandra indexes the repositories rely on.
 *
 * Runs after Spring Data has created the tables (schema-action), which is why this lives in the app
 * and not in the cassandra-init container: on a fresh database the orders table doesn't exist yet
 * when cassandra-init runs.
 */
@Component
public class CassandraIndexes {

    private final CassandraTemplate cassandra;

    public CassandraIndexes(CassandraTemplate cassandra) {
        this.cassandra = cassandra;
    }

    @PostConstruct
    void create() {
        // Storage-Attached Index (Cassandra 5): lets us query orders by customer without scanning the table.
        cassandra.getCqlOperations().execute(
                "CREATE INDEX IF NOT EXISTS orders_customerid_idx ON orders (customerid) USING 'sai'");
    }
}
