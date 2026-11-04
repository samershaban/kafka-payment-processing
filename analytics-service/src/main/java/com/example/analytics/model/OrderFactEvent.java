package com.example.analytics.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One record on analytics.orders.raw: the full state of an order at the moment something happened to it.
 *
 * @param eventId    deterministic id: the same source event always gets the same eventId, so downstream
 *                   (Flink) can drop duplicates caused by redelivery
 * @param kind       what happened
 * @param occurredAt when it happened (event time), not when it was processed
 * @param order      the order's state after the change
 */
public record OrderFactEvent(String eventId, Kind kind, Instant occurredAt, OrderSnapshot order) {

    public enum Kind {
        /** An order was placed (from orders.created). */
        CREATED,
        /** The payment outcome arrived (from payments.processed). */
        PAYMENT_RESULT,
        /** Republished from Cassandra by the backfill job (history from before capture started). */
        BACKFILL
    }

    /** All columns of the order. */
    public record OrderSnapshot(
            String id,
            String customerId,
            String item,
            int quantity,
            BigDecimal totalPrice,
            String status,
            String statusReason,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
