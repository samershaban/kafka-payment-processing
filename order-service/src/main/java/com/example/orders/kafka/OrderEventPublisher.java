package com.example.orders.kafka;

import com.example.orders.domain.Order;
import com.example.orders.events.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               @Value("${app.topics.order-created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publishOrderCreated(Order order) {
        var event = new OrderCreatedEvent(
                order.getId(),
                order.getCustomerId(),
                order.getItem(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getCreatedAt());

        // Key = orderId: every event for the same order lands on the same partition,
        // so consumers see them in order.
        kafkaTemplate.send(topic, order.getId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreated for {}", order.getId(), ex);
                    } else {
                        var md = result.getRecordMetadata();
                        log.info("Published OrderCreated {} -> {}-{} @ offset {}",
                                order.getId(), md.topic(), md.partition(), md.offset());
                    }
                });
    }
}
