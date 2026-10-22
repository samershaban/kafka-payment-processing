package com.example.payments.events;

import java.math.BigDecimal;
import java.time.Instant;

/** This service's view of the `orders.created` contract. Only needs the fields it uses. */
public record OrderCreatedEvent(
        String orderId,
        String customerId,
        String item,
        int quantity,
        BigDecimal totalPrice,
        Instant createdAt
) {}
