package com.example.payments.events;

import java.time.Instant;

/** Published to `payments.processed`. Keyed by orderId. */
public record PaymentProcessedEvent(
        String orderId,
        boolean success,
        String reason,
        Instant processedAt
) {}
