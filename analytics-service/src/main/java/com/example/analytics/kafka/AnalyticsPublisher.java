package com.example.analytics.kafka;

import com.example.analytics.model.OrderFactEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Publishes analytics records as JSON. Waits for the broker's ack, so a failed send fails the caller. */
@Component
public class AnalyticsPublisher {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String ordersTopic;
    private final String usersTopic;

    public AnalyticsPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json,
                              @Value("${app.topics.orders-raw}") String ordersTopic,
                              @Value("${app.topics.users-raw}") String usersTopic) {
        this.kafka = kafka;
        this.json = json;
        this.ordersTopic = ordersTopic;
        this.usersTopic = usersTopic;
    }

    /** Key = order id, so all facts for one order land on the same partition, in order. */
    public void publish(OrderFactEvent fact) {
        try {
            send(ordersTopic, fact.order().id(), json.writeValueAsString(fact));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Could not serialize " + fact, e);
        }
        log.debug("Published {} fact for order {} (eventId {})", fact.kind(), fact.order().id(), fact.eventId());
    }

    /** A user change, already JSON (Debezium envelope). Key = user id, so each user's changes stay in order. */
    public void publishUserChange(String userId, String changeJson) {
        send(usersTopic, userId, changeJson);
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
