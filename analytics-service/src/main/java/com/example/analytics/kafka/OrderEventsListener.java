package com.example.analytics.kafka;

import com.example.analytics.events.OrderCreatedEvent;
import com.example.analytics.events.PaymentProcessedEvent;
import com.example.analytics.service.OrderFactBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to order-service's and payment-service's events (with its own consumer group, so it doesn't take
 * messages away from them) and turns each one into an order fact on analytics.orders.raw.
 *
 * Messages are read as plain strings and parsed here: the two topics carry different types and the producers
 * don't send type headers.
 *
 * Delivery is at-least-once: the offset is committed after the fact is published, so a crash in between means the
 * event is processed again. That's fine because the eventId is deterministic and Flink drops duplicates.
 */
@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final OrderFactBuilder facts;
    private final AnalyticsPublisher publisher;
    private final ObjectMapper json;

    public OrderEventsListener(OrderFactBuilder facts, AnalyticsPublisher publisher, ObjectMapper json) {
        this.facts = facts;
        this.publisher = publisher;
        this.json = json;
    }

    @KafkaListener(topics = "${app.topics.orders-created}")
    public void onOrderCreated(String message) throws JsonProcessingException {
        OrderCreatedEvent event = json.readValue(message, OrderCreatedEvent.class);
        publisher.publish(facts.fromCreated(event));
        log.info("Order {} created -> analytics fact published", event.orderId());
    }

    @KafkaListener(topics = "${app.topics.payments-processed}")
    public void onPaymentProcessed(String message) throws JsonProcessingException {
        PaymentProcessedEvent event = json.readValue(message, PaymentProcessedEvent.class);
        facts.fromPaymentResult(event).ifPresent(fact -> {
            publisher.publish(fact);
            log.info("Order {} payment {} -> analytics fact published", event.orderId(), fact.order().status());
        });
    }
}
