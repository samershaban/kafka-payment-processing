package com.example.orders.domain;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * Remembers which order an Idempotency-Key created, so a retried POST /orders returns that order
 * instead of creating a duplicate. Rows expire automatically (see IdempotencyStore.TTL).
 */
@Table("idempotency_keys")
public class IdempotencyRecord {

    @PrimaryKey
    private String key;

    /** SHA-256 of the request body, to catch the same key being reused for a different order. */
    private String requestHash;

    private String orderId;
    private Instant createdAt;

    protected IdempotencyRecord() {
        // for Spring Data
    }

    public IdempotencyRecord(String key, String requestHash, String orderId) {
        this.key = key;
        this.requestHash = requestHash;
        this.orderId = orderId;
        this.createdAt = Instant.now();
    }

    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public String getOrderId() { return orderId; }
    public Instant getCreatedAt() { return createdAt; }
}
