package com.example.analytics.service;

import com.example.analytics.kafka.AnalyticsPublisher;
import com.example.analytics.repository.OrderScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Republishes every existing order from Cassandra as a BACKFILL fact, so analytics also has the history from
 * before capture started. Safe to repeat: eventIds are deterministic (order id + updatedAt), so Flink drops repeats.
 */
@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    private final OrderScanner scanner;
    private final OrderFactBuilder facts;
    private final AnalyticsPublisher publisher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BackfillService(OrderScanner scanner, OrderFactBuilder facts, AnalyticsPublisher publisher) {
        this.scanner = scanner;
        this.facts = facts;
        this.publisher = publisher;
    }

    public record Result(long published, Duration took) {}

    /** Runs the backfill now (synchronously). Only one at a time. */
    public Result backfillOrders() {
        if (!running.compareAndSet(false, true)) {
            throw new BackfillAlreadyRunningException();
        }
        try {
            long started = System.nanoTime();
            long published = scanner.forEachOrder(order -> publisher.publish(facts.fromBackfill(order)));
            Duration took = Duration.ofNanos(System.nanoTime() - started);
            log.info("Backfill published {} orders in {} ms", published, took.toMillis());
            return new Result(published, took);
        } finally {
            running.set(false);
        }
    }

    public static class BackfillAlreadyRunningException extends RuntimeException {
        public BackfillAlreadyRunningException() {
            super("A backfill is already running");
        }
    }
}
