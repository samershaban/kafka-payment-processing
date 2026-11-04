package com.example.orders.repository;

import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.example.orders.model.IdempotencyRecord;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.cassandra.core.cql.QueryOptions;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

import static org.springframework.data.cassandra.core.query.Criteria.where;

@Component
public class IdempotencyStore {

    /** How long a key is remembered. Retries after this create a new order. */
    public static final Duration TTL = Duration.ofHours(24);

    private final CassandraTemplate cassandra;

    public IdempotencyStore(CassandraTemplate cassandra) {
        this.cassandra = cassandra;
    }

    /**
     * Atomically claims the key for a new order.
     *
     * @return empty if this caller won the claim (go ahead and create the order), or the existing
     *         record if the key was already claimed by an earlier request.
     * @throws KeyNotVisibleException if the claim was lost but the winner's record can't be read
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

        // We lost the claim. Read at SERIAL consistency so we're guaranteed to see the winner's Paxos write
        // (a normal LOCAL_ONE read could hit a replica that doesn't have it yet on a multi-node cluster).
        var query = Query.query(where("key").is(key))
                .queryOptions(QueryOptions.builder().consistencyLevel(DefaultConsistencyLevel.SERIAL).build());
        IdempotencyRecord existing = cassandra.selectOne(query, IdempotencyRecord.class);
        if (existing == null) {
            // Never treat "not found" as "we won": that would create a duplicate order.
            throw new KeyNotVisibleException(key);
        }
        return Optional.of(existing);
    }

    /** Frees a key whose order could not be saved, so the client's retry can succeed. */
    public void release(String key) {
        cassandra.deleteById(key, IdempotencyRecord.class);
    }

    /** The claim was lost, but the winning record isn't readable (e.g. it just expired). Safe answer: retry. */
    public static class KeyNotVisibleException extends RuntimeException {
        public KeyNotVisibleException(String key) {
            super("Idempotency-Key " + key + " was claimed by another request but could not be read");
        }
    }
}
