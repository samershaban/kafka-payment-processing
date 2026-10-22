package com.example.orders.events;

import java.math.BigDecimal;
import java.time.Instant;

/** Published to `orders.created` when a new order is placed. Keyed by orderId. */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        String item,
        int quantity,
        BigDecimal totalPrice,
        Instant createdAt
) {}
