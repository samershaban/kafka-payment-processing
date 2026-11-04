package com.example.analytics.kafka;

import com.example.analytics.model.OrderFactEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Publishes analytics records as JSON. Waits for the broker's ack, so a failed send fails the caller.
 * Counts what it publishes (metric analytics.published, tag topic) for /admin/status and /actuator/metrics.
 */
@Component
public class AnalyticsPublisher {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String ordersTopic;
    private final String usersTopic;

    private final Counter ordersPublished;
    private final Counter usersPublished;
    private final AtomicReference<Instant> lastOrderAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastUserAt = new AtomicReference<>();

    public AnalyticsPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json, MeterRegistry metrics,
                              @Value("${app.topics.orders-raw}") String ordersTopic,
                              @Value("${app.topics.users-raw}") String usersTopic) {
        this.kafka = kafka;
        this.json = json;
        this.ordersTopic = ordersTopic;
        this.usersTopic = usersTopic;
        this.ordersPublished = Counter.builder("analytics.published").tag("topic", ordersTopic)
                .description("Records published to the analytics topics").register(metrics);
        this.usersPublished = Counter.builder("analytics.published").tag("topic", usersTopic)
                .description("Records published to the analytics topics").register(metrics);
    }

    /** Totals since startup and when the last record of each kind was published. */
    public record Stats(long orders, Instant lastOrderAt, long users, Instant lastUserAt) {}

    /** Key = order id, so all facts for one order land on the same partition, in order. */
    public void publish(OrderFactEvent fact) {
        try {
            send(ordersTopic, fact.order().id(), json.writeValueAsString(fact));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize " + fact, e);
        }
        ordersPublished.increment();
        lastOrderAt.set(Instant.now());
        log.debug("Published {} fact for order {} (eventId {})", fact.kind(), fact.order().id(), fact.eventId());
    }

    /** A user change, already JSON (Debezium envelope). Key = user id, so each user's changes stay in order. */
    public void publishUserChange(String userId, String changeJson) {
        send(usersTopic, userId, changeJson);
        usersPublished.increment();
        lastUserAt.set(Instant.now());
    }

    public Stats stats() {
        return new Stats((long) ordersPublished.count(), lastOrderAt.get(),
                (long) usersPublished.count(), lastUserAt.get());
    }

    private void send(String topic, String key, String value) {
        try {
            kafka.send(topic, key, value).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            // Rethrow so the caller fails and the source is redelivered (nothing is silently lost)
            throw new IllegalStateException("Publishing to " + topic + " failed", e);
        }
    }
}
