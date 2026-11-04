package com.example.analytics.events;

import java.math.BigDecimal;
import java.time.Instant;

/** This service's view of the `orders.created` contract (published by order-service). Keyed by orderId. */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        String item,
        int quantity,
        BigDecimal totalPrice,
        Instant createdAt
) {}
