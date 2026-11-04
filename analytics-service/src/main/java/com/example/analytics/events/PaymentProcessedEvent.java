package com.example.analytics.events;

import java.time.Instant;

/** This service's view of the `payments.processed` contract (published by payment-service). Keyed by orderId. */
public record PaymentProcessedEvent(
        String orderId,
        boolean success,
        String reason,
        Instant processedAt
) {}
