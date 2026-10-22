package com.example.orders.events;

import java.time.Instant;

/** Consumed from `payments.processed`. The Payment Service owns the producing side of this contract. */
public record PaymentProcessedEvent(
        String orderId,
        boolean success,
        String reason,
        Instant processedAt
) {}
