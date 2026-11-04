package com.example.orders.domain;

import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class IdempotencyStore {

    /** How long a key is remembered. Retries after this create a new order. */
    static final Duration TTL = Duration.ofHours(24);

    private final CassandraTemplate cassandra;

    public IdempotencyStore(CassandraTemplate cassandra) {
        this.cassandra = cassandra;
    }

    /**
     * Atomically claims the key for a new order.
     *
     * @return empty if this caller won the claim (go ahead and create the order), or the existing
     *         record if the key was already claimed by an earlier request.
     */
    public Optional<IdempotencyRecord> claim(String key, String requestHash, String orderId) {
        // INSERT ... IF NOT EXISTS is a Cassandra lightweight transaction (Paxos): if two requests with
        // the same key arrive at the same moment, exactly one insert is applied. A plain
        // "read, then insert if missing" would let both through.
        var options = InsertOptions.builder().withIfNotExists().ttl(TTL).build();
        boolean claimed = cassandra.insert(new IdempotencyRecord(key, requestHash, orderId), options).wasApplied();
        if (claimed) {
            return Optional.empty();
        }
        return Optional.ofNullable(cassandra.selectOneById(key, IdempotencyRecord.class));
    }

    /** Frees a key whose order could not be saved, so the client's retry can succeed. */
    public void release(String key) {
        cassandra.deleteById(key, IdempotencyRecord.class);
    }
}
