package com.example.analytics.service;

import com.example.analytics.events.OrderCreatedEvent;
import com.example.analytics.events.PaymentProcessedEvent;
import com.example.analytics.model.Order;
import com.example.analytics.model.OrderFactEvent;
import com.example.analytics.model.OrderFactEvent.Kind;
import com.example.analytics.model.OrderFactEvent.OrderSnapshot;
import com.example.analytics.repository.OrderReadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Turns order-service / payment-service events into complete order facts, reading the order from Cassandra. */
@Service
public class OrderFactBuilder {

    private static final Logger log = LoggerFactory.getLogger(OrderFactBuilder.class);

    static final String CREATED = "CREATED";
    static final String PAID = "PAID";
    static final String PAYMENT_FAILED = "PAYMENT_FAILED";

    private final OrderReadRepository orders;

    public OrderFactBuilder(OrderReadRepository orders) {
        this.orders = orders;
    }

    /**
     * An order was placed. The fact describes the order <em>at creation</em>: status CREATED, no reason,
     * updatedAt = createdAt. The row is read for the order's details, but its status is ignored, because by the
     * time this runs (after a lag, a restart or a replay) the order may already be PAID, and the CREATED fact
     * must not say so. If the row is missing (e.g. deleted since), the event alone is used.
     */
    public OrderFactEvent fromCreated(OrderCreatedEvent event) {
        OrderSnapshot snapshot = orders.findById(event.orderId())
                .map(o -> new OrderSnapshot(o.getId(), o.getCustomerId(), o.getItem(), o.getQuantity(),
                        o.getTotalPrice(), CREATED, null, o.getCreatedAt(), o.getCreatedAt()))
                .orElseGet(() -> new OrderSnapshot(event.orderId(), event.customerId(), event.item(),
                        event.quantity(), event.totalPrice(), CREATED, null, event.createdAt(), event.createdAt()));
        return new OrderFactEvent(eventId(Kind.CREATED, event.orderId()), Kind.CREATED, event.createdAt(), snapshot);
    }

    /**
     * The payment outcome arrived. order-service consumes the same event at the same time and may not have saved
     * the new status yet, so the order's details come from Cassandra but the outcome comes from the event itself
     * (the authoritative source). Empty if the order doesn't exist.
     */
    public Optional<OrderFactEvent> fromPaymentResult(PaymentProcessedEvent event) {
        Optional<Order> row = orders.findById(event.orderId());
        if (row.isEmpty()) {
            log.warn("Payment result for unknown order {}; skipped", event.orderId());
            return Optional.empty();
        }
        Order o = row.get();
        var snapshot = new OrderSnapshot(o.getId(), o.getCustomerId(), o.getItem(), o.getQuantity(),
                o.getTotalPrice(),
                event.success() ? PAID : PAYMENT_FAILED,
                event.success() ? null : event.reason(),
                o.getCreatedAt(),
                event.processedAt());
        return Optional.of(new OrderFactEvent(
                eventId(Kind.PAYMENT_RESULT, event.orderId(), String.valueOf(event.processedAt())),
                Kind.PAYMENT_RESULT, event.processedAt(), snapshot));
    }

    /** A historical order, republished as it is now in Cassandra. */
    public OrderFactEvent fromBackfill(Order order) {
        return new OrderFactEvent(
                eventId(Kind.BACKFILL, order.getId(), String.valueOf(order.getUpdatedAt())),
                Kind.BACKFILL, order.getUpdatedAt(), snapshot(order));
    }

    static OrderSnapshot snapshot(Order o) {
        return new OrderSnapshot(o.getId(), o.getCustomerId(), o.getItem(), o.getQuantity(), o.getTotalPrice(),
                o.getStatus(), o.getStatusReason(), o.getCreatedAt(), o.getUpdatedAt());
    }

    /** Same inputs → same id (name-based UUID), so a redelivered source event produces the same eventId. */
    static String eventId(Kind kind, String... parts) {
        String name = kind + ":" + String.join(":", parts);
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
